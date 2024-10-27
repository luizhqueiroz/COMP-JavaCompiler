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

public class InvalidCondition extends AnalysisVisitor {

    @Override
    public void buildVisitor() {
        addVisit(Kind.IF_STMT, this::visitIfStmtOrWhileStmt);
        addVisit(Kind.WHILE_STMT, this::visitIfStmtOrWhileStmt);
        addVisit(Kind.METHOD_CALL_EXPR, this::visitMethodCallExpr);
        addVisit(Kind.METHOD_DECL, this::visitMethodDeclExpr);
        addVisit(Kind.ARRAY_LENGTH_EXPR, this::visitArrayLengthExpr);
    }

    //Check if the method length for an array
    private Void visitArrayLengthExpr(JmmNode arrayLengthExpr, SymbolTable table) {
        String methodName = arrayLengthExpr.get("name");
        JmmNode child = arrayLengthExpr.getChild(0);
        if (!methodName.equals("length")) {
            var message = String.format("Method %s needs to be length", methodName);
            addReport(Report.newError(
                    Stage.SEMANTIC,
                    NodeUtils.getLine(arrayLengthExpr),
                    NodeUtils.getColumn(arrayLengthExpr),
                    message,
                    null)
            );

            return null;
        }
        Type exprType = TypeUtils.getExprType(child, table);
        if (exprType == null) return null;
        if (!exprType.isArray()) {
            var message = String.format("Method %s needs to be applied in arrays", methodName);
            addReport(Report.newError(
                    Stage.SEMANTIC,
                    NodeUtils.getLine(child),
                    NodeUtils.getColumn(child),
                    message,
                    null)
            );
        }

        return null;
    }

    //Check incompatible return type
    private Void visitMethodDeclExpr(JmmNode methodDeclExpr, SymbolTable table) {

        String methodName = methodDeclExpr.get("name");

        JmmNode methodReturnTypeExpr = methodDeclExpr.getChildren(Kind.RETURN_STMT).get(0).getChild(0);

        if (methodReturnTypeExpr.getKind().equals(Kind.METHOD_CALL_EXPR.toString())) {
            Type varType = TypeUtils.getExprType(methodReturnTypeExpr.getChild(0), table);
            if (varType == null) return null;
            if (!varType.getName().equals(table.getClassName())) return null;
            else if (!table.getSuper().isEmpty()) return null;
        }

        if (table.getReturnType(methodName).equals(TypeUtils.getExprType(methodReturnTypeExpr, table))) return null;

        var message = String.format("Method %s has incompatible return type", methodName);
        addReport(Report.newError(
                Stage.SEMANTIC,
                NodeUtils.getLine(methodDeclExpr),
                NodeUtils.getColumn(methodDeclExpr),
                message,
                null)
        );


        return null;
    }

    //Check incompatible arguments in method call
    private Void visitMethodCallIncompatibleArguments(JmmNode methodCallExpr, SymbolTable table) {
        String methodCall = methodCallExpr.get("name");
        boolean sameArgNumber = false;
        boolean compatibleTypes = true;

        List<JmmNode> children = methodCallExpr.getChildren();
        int n1 = children.size();
        List<Symbol> params = table.getParameters(methodCall);
        int n2 = params.size();

        if (n1 - 1 == n2) sameArgNumber = true;
        else if (n2 > 0 && params.get(n2 - 1).getType().hasAttribute("varArg")) {
            if (n1 >= n2) sameArgNumber = true;
        }

        for (int i = 1; i < n1; i++) {
            Type paramType = params.get(i - 1).getType();
            if (paramType.hasAttribute("varArg")) {
                if (sameArgNumber) {
                    Type childType = TypeUtils.getExprType(children.get(i), table);
                    if (childType == null) compatibleTypes = true;
                    else if (!childType.getName().equals(paramType.getName()))
                        compatibleTypes = false;
                } else {
                    for (int j = i; j < n1; j++) {
                        Type exprType = TypeUtils.getExprType(children.get(j), table);
                        if (exprType == null) compatibleTypes = true;
                        else if (exprType.isArray()) {
                            compatibleTypes = false;
                            break;
                        } else if (!exprType.getName().equals(paramType.getName()))
                            compatibleTypes = false;
                    }
                    sameArgNumber = true;
                }
                break;
            }
            Type childType = TypeUtils.getExprType(children.get(i), table);
            if (childType == null) compatibleTypes = true;
            else if (!childType.equals(paramType)) compatibleTypes = false;
        }

        if (!compatibleTypes) {
            // Create error report
            var message = String.format("The types of arguments of the call are incompatible with the types in the declaration of method %s", methodCall);
            addReport(Report.newError(
                    Stage.SEMANTIC,
                    NodeUtils.getLine(methodCallExpr),
                    NodeUtils.getColumn(methodCallExpr),
                    message,
                    null)
            );
        }
        if (!sameArgNumber) {
            // Create error report
            var message = String.format("Number of arguments of the call are incompatible with the number in the declaration of method %s", methodCall);
            addReport(Report.newError(
                    Stage.SEMANTIC,
                    NodeUtils.getLine(methodCallExpr),
                    NodeUtils.getColumn(methodCallExpr),
                    message,
                    null)
            );
        }

        return null;
    }

    //check if method call is valid
    private Void visitMethodCallExpr(JmmNode methodCallExpr, SymbolTable table) {

        String methodCall = methodCallExpr.get("name");
        JmmNode varRefExpr = methodCallExpr.getChild(0);
        String exprKind = varRefExpr.getKind();
        Type exprType = TypeUtils.getExprType(varRefExpr, table);

        if (!exprKind.equals(Kind.VAR_REF_EXPR.toString()) && !exprKind.equals(Kind.THIS_EXPR.toString()) && !exprKind.equals(Kind.PAREN_EXPR.toString())) {
            // Create error report
            var message = String.format("Call to method %s is wrong", methodCall);
            addReport(Report.newError(
                    Stage.SEMANTIC,
                    NodeUtils.getLine(methodCallExpr),
                    NodeUtils.getColumn(methodCallExpr),
                    message,
                    null)
            );

            return null;
        }

        if (exprType == null) return null;

        String className = table.getClassName();
        String exprTypeName = exprType.getName();
        //Check if exists in the declared Method
        if (table.getMethods().stream().anyMatch(method -> method.equals(methodCall)) && exprTypeName.equals(className)) {
            visitMethodCallIncompatibleArguments(methodCallExpr, table);
            if (exprType.hasAttribute("isClass") && methodCall.equals("main"))
                return null;
            if (!exprType.hasAttribute("isClass")) return null;
        }

        //Check if the method is from an imported class
        for (String importName : table.getImports()) {
            String[] imports = importName.split("\\.");
            if (imports[imports.length - 1].equals(exprTypeName)) return null;
        }

        //Check if the method is from an extended class
        if (exprTypeName.equals(table.getClassName()) && !table.getSuper().isEmpty()) return null;

        // Create error report
        var message = String.format("Method %s is not declared or wrong", methodCall);
        addReport(Report.newError(
                Stage.SEMANTIC,
                NodeUtils.getLine(methodCallExpr),
                NodeUtils.getColumn(methodCallExpr),
                message,
                null)
        );

        return null;
    }

    //check if conditional stmt is valid
    private Void visitIfStmtOrWhileStmt(JmmNode ifOrWhileStmt, SymbolTable table) {

        JmmNode expr = ifOrWhileStmt.getChild(0);
        Type exprType = TypeUtils.getExprType(expr, table);
        if (exprType == null) return null;
        if (exprType.equals(TypeUtils.getBoolType())) return null;

        // Create error report
        var message = String.format("Expressions in conditions must return a boolean");
        addReport(Report.newError(
                Stage.SEMANTIC,
                NodeUtils.getLine(ifOrWhileStmt),
                NodeUtils.getColumn(ifOrWhileStmt),
                message,
                null)
        );

        return null;
    }
}