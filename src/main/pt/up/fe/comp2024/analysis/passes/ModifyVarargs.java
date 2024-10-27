package pt.up.fe.comp2024.analysis.passes;

import pt.up.fe.comp.jmm.analysis.table.Symbol;
import pt.up.fe.comp.jmm.analysis.table.SymbolTable;
import pt.up.fe.comp.jmm.analysis.table.Type;
import pt.up.fe.comp.jmm.ast.JmmNode;
import pt.up.fe.comp.jmm.ast.JmmNodeImpl;
import pt.up.fe.comp2024.analysis.AnalysisVisitor;
import pt.up.fe.comp2024.ast.Kind;
import pt.up.fe.comp2024.ast.TypeUtils;

import java.util.List;

public class ModifyVarargs extends AnalysisVisitor {

    @Override
    public void buildVisitor() {
        addVisit(Kind.METHOD_DECL, this::visitMethodDecl);
        addVisit(Kind.METHOD_CALL_EXPR, this::visitMethodCallExpr);
    }

    private Void visitMethodDecl(JmmNode methodDecl, SymbolTable table) {
        List<JmmNode> params = methodDecl.getChildren(Kind.PARAM);

        if (params.isEmpty()) return null;

        JmmNode lastParamType = params.get(params.size() - 1).getChild(0);

        if (lastParamType.hasAttribute("varArg"))
            lastParamType.put("array", "[");

        return null;
    }

    private Void visitMethodCallExpr(JmmNode methodCallExpr, SymbolTable table) {
        String methodName = methodCallExpr.get("name");
        List<Symbol> params = table.getParameters(methodName);
        int n1 = params.size();

        if (params.isEmpty() || !params.get(n1 - 1).getType().hasAttribute("varArg")) return null;

        List<JmmNode> children = methodCallExpr.getChildren();
        int n2 = children.size();

        JmmNodeImpl array = new JmmNodeImpl(Kind.ARRAY_EXPR.toString());
        if (n1 != n2 - 1) {
            for (int i = n1; i < n2; i++) {
                JmmNode node = children.get(i);
                node.detach();
                array.add(node);
            }
            methodCallExpr.add(array);
        } else {
            JmmNode node = children.get(n1);
            Type nodeType = TypeUtils.getExprType(node, table);
            if (!nodeType.isArray()) {
                node.detach();
                array.add(node);
                methodCallExpr.add(array);
            }
        }

        return null;
    }
}
