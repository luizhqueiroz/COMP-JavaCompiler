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

public class IncompatibleOperation extends AnalysisVisitor {

    @Override
    public void buildVisitor() {
        addVisit(Kind.BINARY_EXPR, this::visitBinaryExpr);
    }

    private Boolean compatibleOperation(JmmNode expr, SymbolTable table) {

        List<JmmNode> children = expr.getChildren();
        Type exprType = TypeUtils.getExprType(expr, table);


        if (!expr.getKind().equals(Kind.BINARY_EXPR.toString()))//(expr.getKind().equals(Kind.INTEGER_LITERAL.toString()) || expr.getKind().equals(Kind.BOOLEAN_LITERAL.toString()) || expr.getKind().equals(Kind.VAR_REF_EXPR.toString()))
            return true;

        if (expr.get("op").equals("<") && children.stream().allMatch(child -> {
            Type childType = TypeUtils.getExprType(child, table);
            if (childType != null) return childType.equals(TypeUtils.getIntType());
            return true;
        }))
            return compatibleOperation(children.get(0), table) && compatibleOperation(children.get(1), table);

        else if (children.stream().allMatch(child -> {
            Type childType = TypeUtils.getExprType(child, table);
            if (childType != null) return childType.equals(exprType);
            return true;
        })) {
            return compatibleOperation(children.get(0), table) && compatibleOperation(children.get(1), table);
        }

        return false;
    }

    private Void visitBinaryExpr(JmmNode binaryExpr, SymbolTable table) {

        if (compatibleOperation(binaryExpr, table)) return null;

        // Create error report
        var message = String.format("Operands of an operation %s must have types compatible with the operation", binaryExpr.get("op"));
        addReport(Report.newError(
                Stage.SEMANTIC,
                NodeUtils.getLine(binaryExpr),
                NodeUtils.getColumn(binaryExpr),
                message,
                null)
        );

        return null;
    }
}

