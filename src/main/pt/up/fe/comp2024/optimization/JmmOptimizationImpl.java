package pt.up.fe.comp2024.optimization;

import org.specs.comp.ollir.*;
import pt.up.fe.comp.jmm.analysis.JmmSemanticsResult;
import pt.up.fe.comp.jmm.ollir.JmmOptimization;
import pt.up.fe.comp.jmm.ollir.OllirResult;
import pt.up.fe.comp2024.CompilerConfig;
import pt.up.fe.comp.jmm.report.Report;
import pt.up.fe.comp.jmm.report.Stage;

import java.util.*;

public class JmmOptimizationImpl implements JmmOptimization {

    @Override
    public OllirResult toOllir(JmmSemanticsResult semanticsResult) {

        var visitor = new OllirGeneratorVisitor(semanticsResult.getSymbolTable());
        var ollirCode = visitor.visit(semanticsResult.getRootNode());

        return new OllirResult(semanticsResult, ollirCode, Collections.emptyList());
    }

    @Override
    public JmmSemanticsResult optimize(JmmSemanticsResult semanticsResult) {
        // TODO:
        boolean isToOptimize = CompilerConfig.getOptimize(semanticsResult.getConfig());

        if (!isToOptimize) return JmmOptimization.super.optimize(semanticsResult);

        ConstantFoldingVisitor constFoldVisitor = new ConstantFoldingVisitor();
        ConstantPropagationVisitor constPropVisitor = new ConstantPropagationVisitor();
        do {
            constPropVisitor.setModified(false);
            constFoldVisitor.setModified(false);
            constPropVisitor.visit(semanticsResult.getRootNode(), semanticsResult.getSymbolTable());
            constFoldVisitor.visit(semanticsResult.getRootNode(), semanticsResult.getSymbolTable());
        } while (constFoldVisitor.isModificationApplied() || constPropVisitor.isModificationApplied());

        return JmmOptimization.super.optimize(semanticsResult);
    }

    @Override
    public OllirResult optimize(OllirResult ollirResult) {

        var config = ollirResult.getConfig();
        int numRegisters;

        if (config.containsKey("registerAllocation")) {
            numRegisters = Integer.parseInt(config.get("registerAllocation"));

            if (numRegisters < -1) throw new RuntimeException("Invalid number in -r option");
        } else {
            return ollirResult;
        }

        return switch (numRegisters) {
            case -1 -> ollirResult;
            case 0 -> minOptimize(ollirResult);
            default -> optimizeToReg(ollirResult, numRegisters);
        };

    }

    private OllirResult optimizeToReg(OllirResult ollirResult, int numRegisters) {
        var classUnit = ollirResult.getOllirClass();

        classUnit.buildCFGs();
        classUnit.buildVarTables();

        for (var method : classUnit.getMethods()) {
            if (method.isConstructMethod()) continue;
            var minRegisters = getMinRegisters(method);
            var minRegister = method.getParams().size();
            if (!method.isStaticMethod()) minRegister++;

            if (minRegisters > numRegisters) {
                var reports = ollirResult.getReports();

                reports.add(
                        Report.newError(
                                Stage.OPTIMIZATION,
                                -1,
                                -1,
                                String.format("Register allocation failed in method '%s'. The minimum number of registers for this method is %s", method.getMethodName(), minRegisters),
                                null
                        )
                );

                continue;
            }

            var outDef = aliveness(method);

            var graph = generateGraph(outDef);

            var coloring = applyGraphColoring(graph, numRegisters, minRegister);

            if (coloring == null) {
                throw new RuntimeException("Register allocation failed");
            }

            updateRegisters(method, coloring);

            var reports = ollirResult.getReports();
            var message = createReportMapping(coloring, numRegisters, method);

            reports.add(
                    Report.newLog(
                            Stage.OPTIMIZATION,
                            -1,
                            -1,
                            message,
                            null
                    )
            );
        }

        System.out.println(ollirResult.getReports());

        return ollirResult;
    }

    private OllirResult minOptimize(OllirResult ollirResult) {
        var classUnit = ollirResult.getOllirClass();

        classUnit.buildCFGs();
        classUnit.buildVarTables();


        for (var method : classUnit.getMethods()) {
            if (method.isConstructMethod()) continue;
            var minRegisters = getMinRegisters(method);

            var minRegister = method.getParams().size();
            if (!method.isStaticMethod()) minRegister++;

            var outDef = aliveness(method);

            var graph = generateGraph(outDef);

            var coloring = applyGraphColoring(graph, minRegisters, minRegister);

            if (coloring == null) {
                throw new RuntimeException("Register allocation failed");
            }

            updateRegisters(method, coloring);

            var reports = ollirResult.getReports();

            var message = createReportMapping(coloring, minRegisters, method);

            reports.add(
                    Report.newLog(
                            Stage.OPTIMIZATION,
                            -1,
                            -1,
                            message,
                            null
                    )
            );


            /*
            while (true) {
                var minRegister = method.getParams().size();
                if (!method.isStaticMethod()) minRegister++;

                var outDef = aliveness(method);

                var graph = generateGraph(outDef);

                var coloring = applyGraphColoring(graph, attempt, minRegister);

                if (coloring != null) {
                    updateRegisters(method, coloring);

                    var reports = ollirResult.getReports();

                    var message = createReportMapping(coloring, attempt, method);

                    reports.add(
                            Report.newLog(
                                    Stage.OPTIMIZATION,
                                    -1,
                                    -1,
                                    message,
                                    null
                            )
                    );

                    break;
                }

                attempt++;
            } */
        }

        return ollirResult;
    }

    private int getMinRegisters(Method method) {
        var attempt = 0;

        while (true) {
            var minRegister = method.getParams().size();
            if (!method.isStaticMethod()) minRegister++;

            var outDef = aliveness(method);

            var graph = generateGraph(outDef);

            var coloring = applyGraphColoring(graph, attempt, minRegister);

            if (coloring != null) {
                break;
            }

            attempt++;
        }

        return attempt;
    }

    private String createReportMapping(HashMap<String, Integer> coloring, int attempt, Method method) {
        var message = new StringBuilder();

        message.append("Register allocation succeeded in method '").append(method.getMethodName()).append("' with ").append(attempt).append(" registers.\n");

        for (var variable : coloring.keySet()) {
            message.append("Variable '").append(variable).append("' was assigned to register ").append(coloring.get(variable)).append(".\n");
        }

        return message.toString();
    }


    private void updateRegisters(Method method, HashMap<String, Integer> coloring) {
        var varTable = method.getVarTable();

        for (var variable : coloring.keySet()) {
            var reg = coloring.get(variable);
            var varDescriptor = varTable.get(variable);

            if (varDescriptor == null) {
                continue;
            }
            varDescriptor.setVirtualReg(reg);
        }
    }

    private HashMap<String, Integer> applyGraphColoring(Graph graph, int numRegisters, int minRegister) {
        HashMap<String, Integer> coloring = new HashMap<>();
        Stack<String> stack = new Stack<>();

        Graph graphCopy = new Graph(graph.getNodes(), graph.getEdges());

        while (!graphCopy.getNodes().isEmpty()) {
            boolean found = false;
            for (String node : new HashSet<>(graphCopy.getNodes())) {
                if (graphCopy.degree(node) < numRegisters - minRegister) {
                    stack.push(node);
                    graphCopy.removeNode(node);
                    found = true;
                }
            }
            if (!found) {
                return null;
            }
        }

        while (!stack.isEmpty()) {
            String node = stack.pop();
            Set<Integer> neighborColors = new HashSet<>();

            for (String neighbor : graph.getEdges(node)) {
                if (coloring.containsKey(neighbor)) {
                    neighborColors.add(coloring.get(neighbor));
                }
            }

            for (int color = minRegister; color < numRegisters; color++) {
                if (!neighborColors.contains(color)) {
                    coloring.put(node, color);
                    break;
                }
            }
        }

        return coloring;
    }

    private Graph generateGraph(HashMap<Node, HashSet<String>> outDef) {
        Graph graph = new Graph();

        for (var node : outDef.keySet()) {
            var outDefList = outDef.get(node);
            for (var def : outDefList) {
                graph.addNode(def);
            }

            if (outDefList.size() > 1) {
                var outDefListArray = outDefList.toArray();
                for (int i = 0; i < outDefListArray.length - 1; i++) {
                    for (int j = i + 1; j < outDefListArray.length; j++) {
                        graph.addEdge((String) outDefListArray[i], (String) outDefListArray[j]);
                    }
                }
            }
        }

        return graph;
    }

    private HashMap<Node, HashSet<String>> aliveness(Method method) {
        HashMap<Node, HashSet<String>> use = new HashMap<>();
        HashMap<Node, HashSet<String>> def = new HashMap<>();
        HashMap<Node, HashSet<String>> in = new HashMap<>();
        HashMap<Node, HashSet<String>> out = new HashMap<>();
        HashMap<Node, HashSet<String>> outDef = new HashMap<>();

        var instructions = method.getInstructions();

        for (var instruction : instructions) {
            use.put(instruction, uses(instruction));
            def.put(instruction, defs(instruction));
            out.put(instruction, new HashSet<>());

            var inList = new HashSet<>(out.get(instruction));
            inList.removeAll(def.get(instruction));
            inList.addAll(use.get(instruction));

            in.put(instruction, inList);
        }

        boolean changes = true;

        while (changes) {
            changes = false;
            for (var instruction : instructions) {
                var inList = new HashSet<>(out.get(instruction));
                inList.removeAll(def.get(instruction));
                inList.addAll(use.get(instruction));

                if (!inList.equals(in.get(instruction))) {
                    in.put(instruction, inList);
                    changes = true;
                }
            }


            for (var instruction : instructions) {
                var outList = new HashSet<String>();
                for (var successor : instruction.getSuccessors()) {
                    if (successor instanceof Instruction) {
                        outList.addAll(in.get(successor));
                    }
                }

                if (!outList.equals(out.get(instruction))) {
                    out.put(instruction, outList);
                    changes = true;
                }
            }

        }

        for (var instruction : instructions) {
            var outDefList = new HashSet<>(out.get(instruction));
            outDefList.addAll(def.get(instruction));

            outDef.put(instruction, outDefList);
        }

        return outDef;
    }

    private HashSet<String> uses(Instruction instruction) {
        return switch (instruction.getInstType()) {
            case ASSIGN -> uses(((AssignInstruction) instruction).getRhs());
            case BINARYOPER -> binaryUses((BinaryOpInstruction) instruction);  //new HashSet<>(Set.of((Operand) ((BinaryOpInstruction) instruction).getLeftOperand(), (Operand) ((BinaryOpInstruction) instruction).getRightOperand()));
            case UNARYOPER -> unaryUses((UnaryOpInstruction) instruction);//new HashSet<>(Set.of((Operand) ((UnaryOpInstruction) instruction).getOperand()));
            case CALL -> callUses((CallInstruction) instruction);
            case BRANCH -> uses(((CondBranchInstruction) instruction).getCondition());
            case RETURN ->  returnUses((ReturnInstruction) instruction); //new HashSet<>(Set.of((Operand) ((ReturnInstruction) instruction).getOperand()));
            case GETFIELD -> new HashSet<>(Set.of(((GetFieldInstruction) instruction).getField().getName()));
            case NOPER -> noperUses((SingleOpInstruction) instruction);
            default -> new HashSet<>();
        };
    }

    private HashSet<String> unaryUses(UnaryOpInstruction instruction) {
        var operand = instruction.getOperand();
        if (operand instanceof Operand) {
            return new HashSet<>(Set.of(((Operand) operand).getName()));
        }
        return new HashSet<>();
    }

    private HashSet<String> binaryUses(BinaryOpInstruction instruction) {
        var ops = new HashSet<String>();
        if (instruction.getLeftOperand() instanceof Operand) {
            ops.add(((Operand) instruction.getLeftOperand()).getName());
        }
        if (instruction.getRightOperand() instanceof Operand) {
            ops.add(((Operand) instruction.getRightOperand()).getName());
        }
        return ops;
    }

    private HashSet<String> returnUses(ReturnInstruction instruction) {
        if (instruction.getReturnType().getTypeOfElement() != ElementType.VOID) {
            if(instruction.getOperand() instanceof Operand)
                return new HashSet<>(Set.of(((Operand) instruction.getOperand()).getName()));
        }
        return new HashSet<>();
    }

    private HashSet<String> noperUses(SingleOpInstruction instruction) {
        var operand = instruction.getSingleOperand();
        if (operand.isLiteral()) {
            return new HashSet<>();
        }
        return new HashSet<>(Set.of(((Operand) operand).getName()));
    }

    private HashSet<String> callUses(CallInstruction instruction) {
        var args = instruction.getArguments();
        var ops = new HashSet<String>();
        for (var arg : args) {
            if(arg instanceof Operand)
                ops.add(((Operand) arg).getName());
        }
        return ops;
    }

    private HashSet<String> defs(Instruction instruction) {
        return switch (instruction.getInstType()) {
            case ASSIGN -> new HashSet<>(Set.of(((Operand) ((AssignInstruction) instruction).getDest()).getName()));
            case PUTFIELD -> new HashSet<>(Set.of((((PutFieldInstruction) instruction).getField()).getName()));
            default -> new HashSet<>();
        };
    }

}
