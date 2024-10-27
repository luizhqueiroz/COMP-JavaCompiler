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

public class InvalidVarargs extends AnalysisVisitor {

    @Override
    public void buildVisitor() {
        addVisit(Kind.VAR_DECL, this::visitVarDecl);
        addVisit(Kind.METHOD_DECL, this::visitMethodDecl);
    }

    private Void visitMethodDecl(JmmNode methodDecl, SymbolTable table) {
        String methodDeclName = methodDecl.get("name");
        JmmNode returnExpr = methodDecl.getChildren(Kind.RETURN_STMT).get(0).getChild(0);


        Type returnExprType = TypeUtils.getExprType(returnExpr, table);
        if (returnExprType == null) return null;
        // Check return type vararg
        if (table.getReturnType(methodDeclName).hasAttribute("varArg") || returnExprType.hasAttribute("varArg")) {
            // Create error report
            String message = String.format("Method %s return cannot be vararg", methodDeclName);
            addReport(Report.newError(
                    Stage.SEMANTIC,
                    NodeUtils.getLine(returnExpr),
                    NodeUtils.getColumn(returnExpr),
                    message,
                    null)
            );
        }

        int varArgSum = 0;
        boolean lastParam = false;

        List<Symbol> params = table.getParameters(methodDeclName);
        int n = params.size();
        for (int i = 0; i < n; i++) {
            if (params.get(i).getType().hasAttribute("varArg")) {
                if (i == n - 1) lastParam = true;
                varArgSum++;
            }
        }

        if (varArgSum > 1) {
            String message = String.format("Only one parameter can be vararg in the method declaration %s", methodDeclName);
            addReport(Report.newError(
                    Stage.SEMANTIC,
                    NodeUtils.getLine(methodDecl),
                    NodeUtils.getColumn(methodDecl),
                    message,
                    null)
            );
        }

        if (varArgSum == 1 && !lastParam) {
            String message = String.format("A vararg type must always be the type of the last parameter in the method declaration %s", methodDeclName);
            addReport(Report.newError(
                    Stage.SEMANTIC,
                    NodeUtils.getLine(methodDecl),
                    NodeUtils.getColumn(methodDecl),
                    message,
                    null)
            );
        }

        return null;
    }

    private Void visitVarDecl(JmmNode varDecl, SymbolTable table) {
        //Check variable declaration or field declaration being vararg
        String varName = varDecl.get("name");
        if (!varDecl.getChild(0).hasAttribute("varArg")) return null;

        // Create error report
        var message = String.format("Variable declaration or field declaration %s cannot be vararg", varName);
        addReport(Report.newError(
                Stage.SEMANTIC,
                NodeUtils.getLine(varDecl),
                NodeUtils.getColumn(varDecl),
                message,
                null)
        );

        return null;
    }
}