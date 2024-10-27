package pt.up.fe.comp2024.analysis.passes;

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

public class InvalidArray extends AnalysisVisitor {

    @Override
    public void buildVisitor() {
        addVisit(Kind.ARRAY_EXPR, this::visitArrayExpr);
        addVisit(Kind.ARRAY_ACCESS_EXPR, this::visitArrayAccessExpr);
    }

    private Void visitArrayAccessExpr(JmmNode arrayAccessExpr, SymbolTable table) {
        List<JmmNode> children = arrayAccessExpr.getChildren();
        Type exprType1 = TypeUtils.getExprType(children.get(0), table);
        Type exprType2 = TypeUtils.getExprType(children.get(1), table);
        if (exprType1 != null && !exprType1.isArray()) {
            var message = "Array access is done over an array.";
            addReport(Report.newError(
                    Stage.SEMANTIC,
                    NodeUtils.getLine(arrayAccessExpr),
                    NodeUtils.getColumn(arrayAccessExpr),
                    message,
                    null)
            );
        }

        if (exprType2 != null && !exprType2.equals(TypeUtils.getIntType())) {
            var message = "Array access index is an expression of type integer.";
            addReport(Report.newError(
                    Stage.SEMANTIC,
                    NodeUtils.getLine(arrayAccessExpr),
                    NodeUtils.getColumn(arrayAccessExpr),
                    message,
                    null)
            );
        }

        return null;
    }

    private Void visitArrayExpr(JmmNode arrayExpr, SymbolTable table) {

        if (arrayExpr.getChildren().stream().allMatch(child -> {
            Type childType = TypeUtils.getExprType(child, table);
            if (childType != null)
                return childType.getName().equals(TypeUtils.getIntTypeName());
            return true;
        }))
            return null;

        // Create error report
        var message = "Wrong array initialization";
        addReport(Report.newError(
                Stage.SEMANTIC,
                NodeUtils.getLine(arrayExpr),
                NodeUtils.getColumn(arrayExpr),
                message,
                null)
        );

        return null;
    }
}