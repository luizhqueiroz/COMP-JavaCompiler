package pt.up.fe.comp2024.optimization;

import org.antlr.v4.runtime.misc.Pair;
import org.antlr.v4.runtime.misc.Triple;
import pt.up.fe.comp.jmm.analysis.table.SymbolTable;
import pt.up.fe.comp.jmm.ast.JmmNode;
import pt.up.fe.comp.jmm.ast.JmmNodeImpl;
import pt.up.fe.comp.jmm.ast.PreorderJmmVisitor;
import pt.up.fe.comp2024.ast.Kind;

import java.util.HashMap;
import java.util.List;

public class ConstantPropagationVisitor extends PreorderJmmVisitor<SymbolTable, Void> {
    boolean modified;
    private String currentMethod;
    HashMap<String, Pair<String, String>> mapGlobal;
    HashMap<String, Pair<String, String>> mapLocal;
    HashMap<String, Triple<JmmNode, JmmNode, Integer>> mapDetached;

    public ConstantPropagationVisitor() {
        this.mapGlobal = new HashMap<>();
        this.mapLocal = new HashMap<>();
        this.mapDetached = new HashMap<>();
        this.currentMethod = null;
        this.modified = false;
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
        addVisit(Kind.METHOD_DECL, this::visitMethodDecl);
        addVisit(Kind.MAIN_METHOD_DECL, this::visitMethodDecl);
        addVisit(Kind.ASSIGN_STMT, this::visitAssignStmt);
        addVisit(Kind.VAR_REF_EXPR, this::visitVarRefExpr);
    }

    private Void visitAssignStmt(JmmNode assignStmt, SymbolTable table) {
        JmmNode child = assignStmt.getChild(0);
        String varName = assignStmt.get("name");
        String childKind = child.getKind();

        boolean isLocal = table.getLocalVariables(this.currentMethod).stream().anyMatch(local -> local.getName().equals(varName)) || table.getParameters(this.currentMethod).stream().anyMatch(param -> param.getName().equals(varName));
        if (this.isFromIfStmt(assignStmt) || this.isFromWHileStmt(assignStmt)) {
            Triple<JmmNode, JmmNode, Integer> triple = mapDetached.get(varName);
            if (triple != null){
                triple.a.add(triple.b, triple.c);
                mapDetached.remove(varName);
            }

            if (isLocal) {
                if (this.mapLocal.containsKey(varName)) {
                    this.checkExpr(child, varName, table);
                    mapLocal.remove(varName);
                }
            } else {
                if (this.mapGlobal.containsKey(varName)) {
                    this.checkExpr(child, varName, table);
                    mapGlobal.remove(varName);
                }
            }

            return null;
        }

        boolean isLiteral = childKind.equals(Kind.INTEGER_LITERAL.toString()) || childKind.equals(Kind.BOOLEAN_LITERAL.toString());
        if (isLiteral) {
            if (isLocal) {
                mapLocal.put(varName, new Pair<>(childKind, child.get("value")));
            } else {
                mapGlobal.put(varName, new Pair<>(childKind, child.get("value")));
            }

            JmmNode parent = assignStmt.getParent();
            int i = parent.removeChild(assignStmt);
            mapDetached.put(varName, new Triple<>(parent, assignStmt, i));
        } else {
            if (isLocal) {
                if (this.mapLocal.containsKey(varName)) {
                    this.checkExpr(child, varName, table);
                    mapLocal.remove(varName);
                }
            } else {
                if (this.mapGlobal.containsKey(varName)) {
                    this.checkExpr(child, varName, table);
                    mapGlobal.remove(varName);
                }
            }
        }

        return null;
    }

    private boolean isFromWHileStmt(JmmNode node) {
        JmmNode parent = node.getParent();

        while (!parent.getKind().equals(Kind.METHOD_DECL.toString()) && !parent.getKind().equals(Kind.MAIN_METHOD_DECL.toString())) {
            if (parent.getKind().equals(Kind.WHILE_STMT.toString()))
                return true;
            parent = parent.getParent();
        }

        return false;
    }

    private boolean isFromIfStmt(JmmNode node) {
        JmmNode parent = node.getParent();

        while (!parent.getKind().equals(Kind.METHOD_DECL.toString()) && !parent.getKind().equals(Kind.MAIN_METHOD_DECL.toString())) {
            if (parent.getKind().equals(Kind.WHILE_STMT.toString()))
                return true;
            parent = parent.getParent();
        }

        return false;
    }

    private void checkExpr(JmmNode expr, String varName, SymbolTable table) {
        if (expr.getKind().equals(Kind.VAR_REF_EXPR.toString()) && expr.get("name").equals(varName)) {
            this.visitVarRefExpr(expr, table);
        }

        List<JmmNode> children = expr.getChildren();
        for (JmmNode child : children) {
            checkExpr(child, varName, table);
        }
    }

    private Void visitVarRefExpr(JmmNode varRefExpr, SymbolTable table) {
        String varName = varRefExpr.get("name");
        Pair<String, String> localValue = mapLocal.get(varName);
        Pair<String, String> globalValue = mapGlobal.get(varName);

        if (this.isFromWHileStmt(varRefExpr)) return null;

        if (localValue != null) {
            JmmNodeImpl node = new JmmNodeImpl(localValue.a);
            node.put("value", localValue.b);
            varRefExpr.replace(node);
            this.modified = true;
        } else if (globalValue != null) {
            JmmNodeImpl node = new JmmNodeImpl(globalValue.a);
            node.put("value", globalValue.b);
            varRefExpr.replace(node);
            this.modified = true;
        }

        return null;
    }

    private Void visitMethodDecl(JmmNode methodDecl, SymbolTable table) {
        this.currentMethod = methodDecl.get("name");
        this.mapLocal.clear();
        this.mapDetached.clear();

        return null;
    }
}
