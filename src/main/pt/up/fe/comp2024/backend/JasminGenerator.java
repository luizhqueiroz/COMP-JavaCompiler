package pt.up.fe.comp2024.backend;

import org.specs.comp.ollir.*;
import org.specs.comp.ollir.tree.TreeNode;
import pt.up.fe.comp.jmm.ollir.OllirResult;
import pt.up.fe.comp.jmm.report.Report;
import pt.up.fe.specs.util.classmap.FunctionClassMap;
import pt.up.fe.specs.util.exceptions.NotImplementedException;
import pt.up.fe.specs.util.utilities.StringLines;

import java.util.ArrayList;
import java.util.List;
import java.util.stream.Collectors;

/**
 * Generates Jasmin code from an OllirResult.
 * <p>
 * One JasminGenerator instance per OllirResult.
 */
public class JasminGenerator {

    private static final String NL = "\n";
    private static final String TAB = "   ";

    private final OllirResult ollirResult;

    List<Report> reports;

    String code;

    Method currentMethod;

    int limitStack;
    int stackHeight;

    private int labelCounter;

    private final FunctionClassMap<TreeNode, String> generators;

    public JasminGenerator(OllirResult ollirResult) {
        this.ollirResult = ollirResult;

        reports = new ArrayList<>();
        code = null;
        currentMethod = null;
        labelCounter = 0;

        this.generators = new FunctionClassMap<>();
        generators.put(ClassUnit.class, this::generateClassUnit);
        generators.put(Method.class, this::generateMethod);
        generators.put(AssignInstruction.class, this::generateAssign);
        generators.put(SingleOpInstruction.class, this::generateSingleOp);
        generators.put(LiteralElement.class, this::generateLiteral);
        generators.put(Operand.class, this::generateOperand);
        generators.put(ArrayOperand.class, this::generateArrayOperand);
        generators.put(BinaryOpInstruction.class, this::generateBinaryOp);
        generators.put(ReturnInstruction.class, this::generateReturn);
        generators.put(CallInstruction.class, this::generateCall);
        generators.put(PutFieldInstruction.class, this::generatePutField);
        generators.put(GetFieldInstruction.class, this::generateGetField);
        generators.put(GotoInstruction.class, this::generateGoto);
        generators.put(SingleOpCondInstruction.class, this::generateSingleOpCond);
        generators.put(OpCondInstruction.class, this::generateOpCond);
        generators.put(UnaryOpInstruction.class, this::generateUnaryOp);
    }

    public List<Report> getReports() {
        return reports;
    }

    public String build() {

        // This way, build is idempotent
        if (code == null) {
            code = generators.apply(ollirResult.getOllirClass());
        }

        return code;
    }

    private String generateUnaryOp(UnaryOpInstruction unaryOp) {
        var code = new StringBuilder();
        code.append(generators.apply(unaryOp.getOperand()));
        code.append("iconst_1").append(NL);
        push(1);
        code.append("ixor").append(NL);
        pop(1);
        return code.toString();
    }

    private String generateSingleOpCond(SingleOpCondInstruction singleOpCond) {
        var code = new StringBuilder();

        code.append(generators.apply(singleOpCond.getOperands().get(0)));
        code.append("ifne ").append(singleOpCond.getLabel()).append(NL);
        pop(1);

        return code.toString();
    }

    private String generateOpCond(OpCondInstruction opCond) {
        var code = new StringBuilder();
        var cond = opCond.getCondition();
        code.append(generators.apply(cond)).append(opCond.getLabel()).append(NL);

        return code.toString();
    }

    private String generateGoto(GotoInstruction gotoInstruction) {
        return "goto " + gotoInstruction.getLabel() + NL;
    }

    private String generatePutField(PutFieldInstruction putFieldInstruction) {
        StringBuilder code = new StringBuilder();
        var field = putFieldInstruction.getField();
        var object = putFieldInstruction.getObject();
        var value = putFieldInstruction.getValue();

        if (object.getType().getTypeOfElement().equals(ElementType.CLASS)) {
            code.append(generators.apply(value));
            code.append("putstatic ").append(getFullClass(object.getType().toString())).append("/").append(field.getName()).append(" ").append(getElementType(value.getType())).append(NL);
            pop(1);
            return code.toString();
        }

        var reg = currentMethod.getVarTable().get(object.getName()).getVirtualReg();
        code.append(loadVar(reg, object.getType().getTypeOfElement())).append(NL);

        code.append(generators.apply(value));

        code.append("putfield ").append(getElementType(object.getType())).append("/").append(field.getName()).append(" ").append(getElementType(value.getType())).append(NL);
        pop(2);

        return code.toString();
    }

    private String generateGetField(GetFieldInstruction getFieldInstruction) {
        StringBuilder code = new StringBuilder();
        var field = getFieldInstruction.getField();
        var object = getFieldInstruction.getObject();

        if (!object.getType().getTypeOfElement().equals(ElementType.CLASS)) {
            var reg = currentMethod.getVarTable().get(object.getName()).getVirtualReg();
            code.append(loadVar(reg, object.getType().getTypeOfElement())).append(NL);

            code.append("getfield ").append(getElementType(object.getType())).append("/").append(field.getName()).append(" ").append(getElementType(field.getType())).append(NL);
        }
        else {
            code.append("getstatic ").append(getFullClass(object.getType().toString())).append("/").append(field.getName()).append(" ").append(getElementType(field.getType())).append(NL);
            push(1);
        }

        return code.toString();

    }

    private String generateCall(CallInstruction callInstruction) {
        var code = new StringBuilder();
        var invocationType = callInstruction.getInvocationType();
        var caller = callInstruction.getCaller();
        var classType = caller.getType();

        switch (invocationType) {
            case NEW -> {
                var callerType = caller.getType().getTypeOfElement();
                if (callerType.equals(ElementType.ARRAYREF)) {
                    code.append(generators.apply(callInstruction.getOperands().get(1)));
                    code.append("newarray int").append(NL);
                }
                else {
                    code.append("new ").append(getFullClass(classType.toString())).append(NL);
                    push(1);
                }

            }
            case invokespecial -> {
                var variable = getFullClassStatic(callInstruction.getOperands().get(0).toString());
                var reg = currentMethod.getVarTable().get(variable).getVirtualReg();
                code.append(loadVar(reg, ElementType.CLASS)).append(NL);
                int args = 0;
                for (var arg : callInstruction.getArguments()) {
                    code.append(generators.apply(arg));
                    args++;
                }

                pop(args);
                var elementType = caller.getType().getTypeOfElement();

                code.append("invokespecial ");

                if (elementType.equals(ElementType.THIS)) {
                    var className = ollirResult.getOllirClass().getClassName();
                    code.append(className).append("/<init>");
                }
                else {
                    var className = getFullClass(classType.toString());
                    code.append(className).append("/<init>");
                }

                code.append("(");
                for (var arg : callInstruction.getArguments()) {
                    code.append(getElementType(arg.getType()));
                }

                Type returnType = callInstruction.getReturnType();
                code.append(")").append(getElementType(returnType)).append(NL);

                if (returnType.getTypeOfElement().equals(ElementType.VOID)) {
                    pop(1);
                }
            }
            case invokevirtual -> {
                var firstOperand = callInstruction.getOperands().get(0);
                var variable = getFullClassStatic(firstOperand.toString());
                var reg1 = currentMethod.getVarTable().get(variable).getVirtualReg();
                code.append(loadVar(reg1, firstOperand.getType().getTypeOfElement())).append(NL);
                var methodName = getMethod(callInstruction.getMethodName().toString());

                int args = 0;
                for (var arg : callInstruction.getArguments()) {
                    code.append(generators.apply(arg));
                    args++;
                }

                pop(args);

                code.append("invokevirtual ").append(getFullClass(classType.toString())).append("/").append(methodName).append("(");

                for (var arg : callInstruction.getArguments()) {
                    code.append(getElementType(arg.getType()));
                }

                Type returnType = callInstruction.getReturnType();
                code.append(")").append(getElementType(returnType)).append(NL);

                if (returnType.getTypeOfElement().equals(ElementType.VOID)) {
                    pop(1);
                }
            }
            case invokestatic -> {
                var methodName = getMethod(callInstruction.getMethodName().toString());

                int args = 0;
                for (var arg : callInstruction.getArguments()) {
                    code.append(generators.apply(arg));
                    args++;
                }

                pop(args);

                String className = "";
                if (classType.getTypeOfElement().equals(ElementType.CLASS)) {
                    className = getFullClassStatic(caller.toString());
                }
                else {
                    className = getFullClass(classType.toString());
                }

                code.append("invokestatic ").append(className).append("/").append(methodName).append("(");

                for (var arg : callInstruction.getArguments()) {
                    code.append(getElementType(arg.getType()));
                }

                Type returnType = callInstruction.getReturnType();
                code.append(")").append(getElementType(returnType)).append(NL);

                if (!returnType.getTypeOfElement().equals(ElementType.VOID)) {
                    push(1);
                }
            }
            case arraylength -> {
                var variable = (Operand) callInstruction.getOperands().get(0);
                var reg = currentMethod.getVarTable().get(variable.getName()).getVirtualReg();
                code.append(loadVar(reg, variable.getType().getTypeOfElement())).append(NL);
                code.append("arraylength").append(NL);
            }
            default -> throw new NotImplementedException(invocationType);
        }
        return code.toString();
    }

    private String generateClassUnit(ClassUnit classUnit) {

        var code = new StringBuilder();

        // generate class name
        var className = ollirResult.getOllirClass().getClassName();
        code.append(".class ").append(className).append(NL).append(NL);

        var superClass = ollirResult.getOllirClass().getSuperClass();
        if (superClass != null) {
            if (superClass.equals("Object")) {
                if (classUnit.isImportedClass("Object")) {
                    superClass = getFullClass("Object");
                }
                else {
                    superClass = "java/lang/Object";
                }
            }
            code.append(".super ").append(superClass).append(NL);
        }
        else {
            code.append(".super java/lang/Object").append(NL);
        }

        for (Field field : ollirResult.getOllirClass().getFields()) {
            var fieldName = field.getFieldName();
            var fieldType = field.getFieldType();
            var modifier = field.getFieldAccessModifier() != AccessModifier.DEFAULT ?
                    field.getFieldAccessModifier().name().toLowerCase() + " " :
                    "";

            code.append(".field ").append(modifier).append("'").append(fieldName).append("' ")
                    .append(getElementType(fieldType)).append(NL);

        }

        if (superClass != null) {
            var defaultConstructor = """
                ;default constructor
                .method public <init>()V
                    aload_0
                    invokespecial %s/<init>()V
                    return
                .end method
                """.formatted(superClass);
            code.append(defaultConstructor);
        }
        else {
            var defaultConstructor = """
                ;default constructor
                .method public <init>()V
                    aload_0
                    invokespecial java/lang/Object/<init>()V
                    return
                .end method
                """;
            code.append(defaultConstructor);
        }

        // generate code for all other methods
        for (var method : ollirResult.getOllirClass().getMethods()) {

            // Ignore constructor, since there is always one constructor
            // that receives no arguments, and has been already added
            // previously
            if (method.isConstructMethod()) {
                continue;
            }

            code.append(generators.apply(method));
        }

        return code.toString();
    }

    private String generateMethod(Method method) {

        // set method
        currentMethod = method;
        limitStack = 0;

        var code = new StringBuilder();

        // calculate modifier
        var modifier = method.getMethodAccessModifier() != AccessModifier.DEFAULT ?
                method.getMethodAccessModifier().name().toLowerCase() + " " :
                "";

        if (method.isStaticMethod()) {
            modifier += "static ";
        }

        var methodName = method.getMethodName();

        code.append("\n.method ").append(modifier).append(methodName).append("(");

        for (var param : method.getParams()) {
            code.append(getElementType(param.getType()));
        }

        code.append(")");

        var returnType = method.getReturnType();
        code.append(getElementType(returnType)).append(NL);

        var body = new StringBuilder();

        for (var inst : method.getInstructions()) {
            var labels = method.getLabels(inst);
            if (!labels.isEmpty()) {
                body.append(labels.stream().map(label -> label + ":").collect(Collectors.joining(NL + TAB, TAB, NL)));
            }

            var instCode = StringLines.getLines(generators.apply(inst)).stream()
                    .collect(Collectors.joining(NL + TAB, TAB, NL));
            body.append(instCode);

            if (inst instanceof CallInstruction callInstruction && !callInstruction.getReturnType().getTypeOfElement().equals(ElementType.VOID)) {
                body.append(TAB).append("pop").append(NL);
                pop(1);
            }
        }

        // Add limits
        code.append(TAB).append(".limit stack ").append(limitStack).append(NL);
        code.append(TAB).append(".limit locals ").append(calculateLimitLocals(method)).append(NL);

        code.append(body);

        code.append(".end method\n");

        // unset method
        currentMethod = null;

        return code.toString();
    }

    private String generateAssign(AssignInstruction assign) {
        var code = new StringBuilder();

        var lhs = assign.getDest();
        var rhs = assign.getRhs();

        if (!(lhs instanceof Operand operand)) {
            throw new NotImplementedException(lhs.getClass());
        }

        // iinc optimization
        if (rhs instanceof BinaryOpInstruction binaryOp && (binaryOp.getOperation().getOpType().equals(OperationType.ADD) || binaryOp.getOperation().getOpType().equals(OperationType.SUB))) {
            var leftOperand = binaryOp.getLeftOperand();
            if (leftOperand instanceof Operand left) {
                var opName = operand.getName();
                if (opName.equals(left.getName()) && binaryOp.getRightOperand() instanceof LiteralElement literalElement) {
                    var num = Integer.parseInt(literalElement.getLiteral());
                    if (binaryOp.getOperation().getOpType().equals(OperationType.SUB)) {
                        num = -num;
                    }

                    if (num >= -128 && num <= 127) {
                        var reg = currentMethod.getVarTable().get(opName).getVirtualReg();
                        code.append("iinc ").append(reg).append(" ").append(num).append(NL);
                        return code.toString();
                    }
                }
            }
            else if (leftOperand instanceof LiteralElement literalElement) {
                var num = Integer.parseInt(literalElement.getLiteral());
                if (binaryOp.getRightOperand() instanceof Operand right) {
                    var opName = operand.getName();
                    if (opName.equals(right.getName())) {
                        if (binaryOp.getOperation().getOpType().equals(OperationType.ADD)) {
                            if (num >= -128 && num <= 127) {
                                var reg = currentMethod.getVarTable().get(opName).getVirtualReg();
                                code.append("iinc ").append(reg).append(" ").append(num).append(NL);
                                return code.toString();
                            }
                        }
                    }
                }
            }
        }

        // generate code for loading what's on the right


        if (lhs instanceof ArrayOperand arrayOperand) {
            var reg = currentMethod.getVarTable().get(arrayOperand.getName()).getVirtualReg();
            code.append(loadVar(reg, ElementType.ARRAYREF)).append(NL);

            for (var op : arrayOperand.getIndexOperands()) {
                code.append(generators.apply(op));
            }

            var rhsCode = generators.apply(rhs);

            code.append(rhsCode);
            pop(3);
            code.append("iastore").append(NL);

            return code.toString();
        }

        var rhsCode = generators.apply(rhs);

        code.append(rhsCode);

        if (rhs instanceof BinaryOpInstruction binaryOp && checkBranchBinaryOp(binaryOp.getOperation().getOpType())) {
            var label = generateLabel();

            var trueLabel = label + "true";
            var endLabel = label + "end";

            code.append(trueLabel).append(NL);
            code.append("iconst_0").append(NL);
            code.append("goto ").append(endLabel).append(NL);
            code.append(trueLabel).append(":").append(NL);
            code.append("iconst_1").append(NL);
            push(1);
            code.append(endLabel).append(":").append(NL);
        }

        code.append(storeVar(operand)).append(NL);

        return code.toString();
    }

    private String generateLabel() {
        return "cmp_" + labelCounter++ + "_";
    }

    private String generateSingleOp(SingleOpInstruction singleOp) {
        return generators.apply(singleOp.getSingleOperand());
    }

    private String generateLiteral(LiteralElement literal) {
        StringBuilder code = new StringBuilder();
        var literalValue = literal.getLiteral();
        var type = literal.getType().getTypeOfElement();

        switch (type) {
            case INT32, BOOLEAN -> {
                int value = Integer.parseInt(literalValue);

                if (value >= -1 && value <= 5) {
                    code.append("iconst_");
                } else if (value >= -128 && value <= 127) {
                    code.append("bipush ");
                } else if (value >= -32768 && value <= 32767) {
                    code.append("sipush ");
                } else {
                    code.append("ldc ");
                }
                code.append((value == -1) ? "m1" : value).append(NL);
            }
        }

        push(1);

        return code.toString();
    }

    private String generateOperand(Operand operand) {
        var reg = currentMethod.getVarTable().get(operand.getName()).getVirtualReg();
        var type = operand.getType().getTypeOfElement();
        return loadVar(reg, type) + NL;
    }

    private String generateArrayOperand(ArrayOperand arrayOperand) {
        StringBuilder code = new StringBuilder();

        var reg = currentMethod.getVarTable().get(arrayOperand.getName()).getVirtualReg();
        code.append(loadVar(reg, ElementType.ARRAYREF)).append(NL);

        for (var operand : arrayOperand.getIndexOperands()) {
            code.append(generators.apply(operand));
        }

        code.append("iaload").append(NL);

        pop(1);

        return code.toString();
    }

    private String generateBinaryOp(BinaryOpInstruction binaryOp) {
        var code = new StringBuilder();

        // load values on the left and on the right
        code.append(generators.apply(binaryOp.getLeftOperand()));
        code.append(generators.apply(binaryOp.getRightOperand()));

        var opType = binaryOp.getOperation().getOpType();

        // apply operation
        var op = switch (opType) {
            case ADD -> "iadd";
            case MUL -> "imul";
            case DIV -> "idiv";
            case SUB -> "isub";
            case AND, ANDB -> "iand";
            case OR -> "ior";
            case NOTB, NOT -> "ifeq";
            case LTH -> "isub" + NL + "iflt";
            case GTE -> "isub" + NL + "ifge";
            case GTH -> "isub" + NL + "ifgt";
            case EQ -> "isub" + NL + "ifeq";
            case NEQ -> "isub" + NL + "ifne";
            case LTE -> "isub" + NL + "ifle";

            default -> throw new NotImplementedException(binaryOp.getOperation().getOpType());
        };

        switch (opType) {
            case ADD, MUL, DIV, SUB, AND, ANDB, OR, NOTB, NOT -> pop(1);
            case LTH, GTE, NEQ, LTE, GTH, EQ -> pop(2);
        }

        if (checkBranchBinaryOp(opType)) {
            code.append(op).append(" ");
        } else {
            code.append(op).append(NL);
        }

        return code.toString();
    }

    private boolean checkBranchBinaryOp(OperationType opType) {
        return switch (opType) {
            case LTH, NOTB, GTH, EQ, NEQ, LTE, GTE, ANDB, ORB, XOR, NOT, AND, OR -> true;
            default -> false;
        };
    }

    private String generateReturn(ReturnInstruction returnInst) {
        var code = new StringBuilder();

        if (returnInst.hasReturnValue()) {
            code.append(generators.apply(returnInst.getOperand()));
        }

        var returnType = returnInst.getReturnType().getTypeOfElement();

        switch (returnType) {
            case INT32, BOOLEAN -> code.append("ireturn").append(NL);
            case STRING, ARRAYREF, OBJECTREF -> code.append("areturn").append(NL);
            case VOID -> code.append("return").append(NL);
            default -> throw new NotImplementedException(returnType);
        }

        if (!returnType.equals(ElementType.VOID)) {
            pop(1);
        }

        return code.toString();
    }

    private String getFullClass(String simpleClassName) {
        String className = "";
        var classUnit = ollirResult.getOllirClass();
        var imports = classUnit.getImports();
        simpleClassName = simpleClassName.substring(simpleClassName.indexOf("(") + 1, simpleClassName.indexOf(")"));

        if (classUnit.isImportedClass(simpleClassName)) {
            for (var importClass : imports) {
                var fullImport = importClass.split("\\.");
                var simpleImport = fullImport[fullImport.length - 1];
                if (simpleImport.equals(simpleClassName)) {
                    className = importClass.replace(".", "/");
                    break;
                }
            }
        } else {
            className = simpleClassName;
        }

        return className;
    }

    private String getFullClassStatic(String simpleClassName) {
        String className = "";
        var classUnit = ollirResult.getOllirClass();
        var imports = classUnit.getImports();
        simpleClassName = simpleClassName.substring(simpleClassName.indexOf(" ") + 1, simpleClassName.indexOf("."));

        if (classUnit.isImportedClass(simpleClassName)) {
            for (var importClass : imports) {
                var fullImport = importClass.split("\\.");
                var simpleImport = fullImport[fullImport.length - 1];
                if (simpleImport.equals(simpleClassName)) {
                    className = importClass.replace(".", "/");
                    break;
                }
            }
        } else {
            className = simpleClassName;
        }

        return className;
    }

    private String getElementType(Type type) {
        String ret = "";
        if (type.getTypeOfElement().equals(ElementType.ARRAYREF)) {
            switch (type.toString()) {
                case "INT32[]" -> ret = "[I";
                case "STRING[]" -> ret = "[Ljava/lang/String;";
                default -> throw new NotImplementedException(type.toString());
            }
        }
        return switch (type.getTypeOfElement()) {
            case OBJECTREF, CLASS -> "L" + getFullClass(type.toString()) + ";";
            case INT32 -> "I";
            case VOID -> "V";
            case STRING -> "Ljava/lang/String;";
            case BOOLEAN -> "Z";
            case ARRAYREF -> ret;
            case THIS -> ollirResult.getOllirClass().getClassName();
        };
    }

    private String getMethod(String methodName) {
        return methodName.substring(methodName.indexOf("\"") + 1, methodName.lastIndexOf("\""));
    }

    private String storeVar(Operand operand) {
        var reg = currentMethod.getVarTable().get(operand.getName()).getVirtualReg();
        var mod = " ";

        pop(1);

        if (reg >= 0 && reg <= 3) {
            mod = "_";
        }

        return switch (operand.getType().getTypeOfElement()) {
            case INT32, BOOLEAN -> "istore" + mod + reg;
            case STRING, OBJECTREF, CLASS, ARRAYREF -> "astore" + mod + reg;
            case THIS -> "astore_0";
            default -> throw new NotImplementedException(operand.getType().getTypeOfElement());
        };
    }

    private String loadVar(int reg, ElementType type) {
        var mod = " ";

        push(1);

        if (reg >= 0 && reg <= 3) {
            mod = "_";
        }

        return switch (type) {
            case THIS -> "aload_0";
            case INT32, BOOLEAN -> "iload" + mod + reg;
            case STRING, ARRAYREF, OBJECTREF, CLASS -> "aload" + mod + reg;
            default -> throw new NotImplementedException(type);
        };
    }

    private int calculateLimitLocals(Method method) {
        int locals = 0;
        for (var vars : method.getVarTable().values()) {
            if (vars.getVirtualReg() > locals) {
                locals = vars.getVirtualReg();
            }
        }
        return locals + 1;
    }

    private void pop(int n) {
        stackHeight -= n;
    }

    private void push(int n) {
        stackHeight += n;
        limitStack = Math.max(limitStack, stackHeight);
    }
}
