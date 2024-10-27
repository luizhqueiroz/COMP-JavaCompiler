package pt.up.fe.comp2024.optimization;

import pt.up.fe.comp.jmm.analysis.table.SymbolTable;
import pt.up.fe.comp.jmm.ast.JmmNode;
import pt.up.fe.comp.jmm.ast.JmmNodeImpl;
import pt.up.fe.comp.jmm.ast.PreorderJmmVisitor;
import pt.up.fe.comp2024.ast.Kind;

import java.util.List;

public class ConstantFoldingVisitor extends PreorderJmmVisitor<SymbolTable, Void> {
    boolean modified;

    public ConstantFoldingVisitor() {
        modified = false;
        setDefaultValue(() -> null);
    }

    public boolean isModificationApplied() {
        return modified;
    }

    public void setModified(boolean modified) {
        this.modified = modified;
    }

    @Override
    protected void buildVisitor() {
        addVisit(Kind.BINARY_EXPR, this::visitBinaryExpr);
        addVisit(Kind.NOT_EXPR, this::visitNotExpr);
        addVisit(Kind.PAREN_EXPR, this::visitParenExpr);
    }

    private Void visitBinaryExpr(JmmNode binaryExpr, SymbolTable table) {
        List<JmmNode> children = binaryExpr.getChildren();
        JmmNode expr1 = children.get(0);
        JmmNode expr2 = children.get(1);
        String op = binaryExpr.get("op");

        boolean boolResult;
        JmmNodeImpl node;
        if (expr1.getKind().equals(Kind.INTEGER_LITERAL.toString()) && expr2.getKind().equals(Kind.INTEGER_LITERAL.toString())) {
            int intResult;
            switch (op) {
                case "+" -> {
                    intResult = Integer.parseInt(expr1.get("value")) + Integer.parseInt(expr2.get("value"));
                    node = new JmmNodeImpl(Kind.INTEGER_LITERAL.toString());
                    node.put("value", Integer.toString(intResult));
                    binaryExpr.replace(node);
                }
                case "-" -> {
                    intResult = Integer.parseInt(expr1.get("value")) - Integer.parseInt(expr2.get("value"));
                    node = new JmmNodeImpl(Kind.INTEGER_LITERAL.toString());
                    node.put("value", Integer.toString(intResult));
                    binaryExpr.replace(node);
                }
                case "*" -> {
                    intResult = Integer.parseInt(expr1.get("value")) * Integer.parseInt(expr2.get("value"));
                    node = new JmmNodeImpl(Kind.INTEGER_LITERAL.toString());
                    node.put("value", Integer.toString(intResult));
                    binaryExpr.replace(node);
                }
                case "/" -> {
                    intResult = Integer.parseInt(expr1.get("value")) / Integer.parseInt(expr2.get("value"));
                    node = new JmmNodeImpl(Kind.INTEGER_LITERAL.toString());
                    node.put("value", Integer.toString(intResult));
                    binaryExpr.replace(node);
                }
                case "<" -> {
                    boolResult = Integer.parseInt(expr1.get("value")) < Integer.parseInt(expr2.get("value"));
                    node = new JmmNodeImpl(Kind.BOOLEAN_LITERAL.toString());
                    node.put("value", Boolean.toString(boolResult));
                    binaryExpr.replace(node);
                }
            }

            this.modified = true;
        } else if (expr1.getKind().equals(Kind.BOOLEAN_LITERAL.toString()) && expr2.getKind().equals(Kind.BOOLEAN_LITERAL.toString())) {
            switch (op) {
                case "&&" -> {
                    boolResult = Boolean.parseBoolean(expr1.get("value")) && Boolean.parseBoolean(expr2.get("value"));
                    node = new JmmNodeImpl(Kind.BOOLEAN_LITERAL.toString());
                    node.put("value", Boolean.toString(boolResult));
                    binaryExpr.replace(node);
                }
            }

            this.modified = true;
        }

        return null;
    }

    private Void visitNotExpr(JmmNode notExpr, SymbolTable table) {
        JmmNode child = notExpr.getChild(0);

        if (child.getKind().equals(Kind.BOOLEAN_LITERAL.toString())) {
            boolean boolResult = Boolean.parseBoolean(child.get("value"));
            JmmNodeImpl node = new JmmNodeImpl(Kind.BOOLEAN_LITERAL.toString());
            node.put("value", Boolean.toString(!boolResult));
            notExpr.replace(node);
            this.modified = true;
        }

        return null;
    }

    private Void visitParenExpr(JmmNode parenExpr, SymbolTable table) {
        List<JmmNode> children = parenExpr.getChildren();
        if (children.size() == 1) {
            parenExpr.replace(children.get(0));
        }

        return null;
    }
}
