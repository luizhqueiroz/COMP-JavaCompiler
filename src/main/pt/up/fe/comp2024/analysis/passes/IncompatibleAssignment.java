package pt.up.fe.comp2024.analysis.passes;

import pt.up.fe.comp.jmm.analysis.table.Symbol;
import pt.up.fe.comp.jmm.analysis.table.SymbolTable;
import pt.up.fe.comp.jmm.analysis.table.Type;
import pt.up.fe.comp.jmm.ast.JmmNode;
import pt.up.fe.comp.jmm.report.Report;
import pt.up.fe.comp.jmm.report.Stage;
import pt.up.fe.comp2024.analysis.AnalysisVisitor;
import pt.up.fe.comp2024.ast.Kind;
import pt.up.fe.comp2024.ast.NodeUtils;
import pt.up.fe.comp2024.ast.TypeUtils;

import java.util.List;

public class IncompatibleAssignment extends AnalysisVisitor {

    @Override
    public void buildVisitor() {
        addVisit(Kind.ASSIGN_STMT, this::visitAssignStmt);
        addVisit(Kind.ARRAY_ASSIGN_STMT, this::visitArrayAssignStmt);
    }

    private Void visitArrayAssignStmt(JmmNode arrayAssignStmt, SymbolTable table) {
        String arrayRefName = arrayAssignStmt.get("name");
        List<JmmNode> children = arrayAssignStmt.getChildren();
        Type exprType1 = TypeUtils.getExprType(children.get(0), table);
        Type exprType2 = TypeUtils.getExprType(children.get(1), table);

        if (exprType1 != null && !exprType1.equals(TypeUtils.getIntType())) {
            var message = String.format("Array %s access index is an expression of type integer.", arrayRefName);
            addReport(Report.newError(
                    Stage.SEMANTIC,
                    NodeUtils.getLine(arrayAssignStmt),
                    NodeUtils.getColumn(arrayAssignStmt),
                    message,
                    null)
            );
        }

        if (exprType2 != null && !exprType2.equals(TypeUtils.getIntType())) {
            var message = String.format("Type of the assignee must be compatible with the type of array %s.", arrayRefName);
            addReport(Report.newError(
                    Stage.SEMANTIC,
                    NodeUtils.getLine(arrayAssignStmt),
                    NodeUtils.getColumn(arrayAssignStmt),
                    message,
                    null)
            );
        }

        JmmNode parent = arrayAssignStmt.getParent();
        while (!parent.getKind().equals(Kind.METHOD_DECL.toString()) && !parent.getKind().equals(Kind.MAIN_METHOD_DECL.toString())) {
            parent = parent.getParent();
        }

        for (Symbol local : table.getLocalVariables(parent.get("name"))) {
            if (local.getName().equals(arrayRefName)) {
                Type localType = local.getType();
                if (localType.isArray()) return null;
            }
        }

        for (Symbol param : table.getParameters(parent.get("name"))) {
            if (param.getName().equals(arrayRefName)) {
                Type paramType = param.getType();
                if (paramType.isArray()) return null;
            }
        }

        for (Symbol field : table.getFields()) {
            if (field.getName().equals(arrayRefName)) {
                Type fieldType = field.getType();
                if (parent.get("name").equals("main")) {
                    // Create error report
                    var message = String.format("Cannot have a field %s in a static method.", arrayRefName);
                    addReport(Report.newError(
                            Stage.SEMANTIC,
                            NodeUtils.getLine(arrayAssignStmt),
                            NodeUtils.getColumn(arrayAssignStmt),
                            message,
                            null)
                    );
                }
                if (fieldType.isArray()) return null;
            }
        }



        var message = String.format("Variable %s must be declared as an array.", arrayRefName);
        addReport(Report.newError(
                Stage.SEMANTIC,
                NodeUtils.getLine(arrayAssignStmt),
                NodeUtils.getColumn(arrayAssignStmt),
                message,
                null)
        );

        return null;
    }


    private Boolean isFromImportedClass(String className, SymbolTable table) {
        for (String importName : table.getImports()) {
            String[] imports = importName.split("\\.");
            if (imports[imports.length - 1].equals(className)) {
                return true;
            }
        }

        return false;
    }

    private Void visitAssignStmt(JmmNode assignStmt, SymbolTable table) {

        String varRefName = assignStmt.get("name");
        JmmNode child = assignStmt.getChild(0);
        Type childType = TypeUtils.getExprType(child, table);
        String superClass = table.getSuper();
        String mainClass = table.getClassName();

        JmmNode parent = assignStmt.getParent();
        while (!parent.getKind().equals(Kind.METHOD_DECL.toString()) && !parent.getKind().equals(Kind.MAIN_METHOD_DECL.toString())) {
            parent = parent.getParent();
        }

        for (Symbol local : table.getLocalVariables(parent.get("name"))) {
            if (local.getName().equals(varRefName)) {
                Type localType = local.getType();
                if (childType == null) return null;
                if (localType.equals(childType)) return null;
                if (this.isFromImportedClass(localType.getName(), table) && this.isFromImportedClass(childType.getName(), table))
                    return null;

                if (localType.getName().equals(superClass) && childType.getName().equals(mainClass)) return null;
            }
        }

        for (Symbol param : table.getParameters(parent.get("name"))) {
            if (param.getName().equals(varRefName)) {
                Type paramType = param.getType();
                if (childType == null) return null;
                if (paramType.equals(childType)) return null;
                if (this.isFromImportedClass(paramType.getName(), table) && this.isFromImportedClass(childType.getName(), table))
                    return null;
                if (paramType.getName().equals(superClass) && childType.getName().equals(mainClass)) return null;
            }
        }

        for (Symbol field : table.getFields()) {
            if (field.getName().equals(varRefName)) {
                if (parent.get("name").equals("main")) {
                    // Create error report
                    var message = String.format("Cannot have a field %s in a static method.", varRefName);
                    addReport(Report.newError(
                            Stage.SEMANTIC,
                            NodeUtils.getLine(assignStmt),
                            NodeUtils.getColumn(assignStmt),
                            message,
                            null)
                    );

                    return null;
                }
                Type fieldType = field.getType();
                if (childType == null) return null;
                if (fieldType.equals(childType)) return null;
                if (this.isFromImportedClass(fieldType.getName(), table) && this.isFromImportedClass(childType.getName(), table))
                    return null;
                if (fieldType.getName().equals(superClass) && childType.getName().equals(mainClass)) return null;
            }
        }


        var message = String.format("Type of the assignee must be compatible with the assigned %s.", varRefName);
        addReport(Report.newError(
                Stage.SEMANTIC,
                NodeUtils.getLine(assignStmt),
                NodeUtils.getColumn(assignStmt),
                message,
                null)
        );

        return null;
    }

}