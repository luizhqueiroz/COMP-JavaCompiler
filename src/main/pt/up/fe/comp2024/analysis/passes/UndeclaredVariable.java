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
import pt.up.fe.specs.util.SpecsCheck;

/**
 * Checks if the type of the expression in a return statement is compatible with the method return type.
 *
 * @author JBispo
 */
public class UndeclaredVariable extends AnalysisVisitor {

    private String currentMethod;

    @Override
    public void buildVisitor() {
        addVisit(Kind.METHOD_DECL, this::visitMethodDecl);
        addVisit(Kind.MAIN_METHOD_DECL, this::visitMethodDecl);
        addVisit(Kind.VAR_REF_EXPR, this::visitVarRefExpr);
        addVisit(Kind.THIS_EXPR, this::visitThisExpr);
        addVisit(Kind.VAR_DECL, this::visitVarDecl);
        addVisit(Kind.NEW_OBJECT_EXPR, this::visitNewObjectExpr);
    }

    private Void visitMethodDecl(JmmNode method, SymbolTable table) {
        currentMethod = method.get("name");
        return null;
    }

    private Void visitNewObjectExpr(JmmNode newObjectExpr, SymbolTable table) {
        String objectName = newObjectExpr.get("name");

        if (table.getImports().stream().anyMatch(importName -> {
            String[] imports = importName.split("\\.");
            return imports[imports.length - 1].equals(objectName);
        }) || table.getClassName().equals(objectName)) return null;

        var message = String.format("No imported class to match the new object of class %s ", objectName);
        addReport(Report.newError(
                Stage.SEMANTIC,
                NodeUtils.getLine(newObjectExpr),
                NodeUtils.getColumn(newObjectExpr),
                message,
                null)
        );

        return null;
    }

    private Void visitVarDecl(JmmNode varDecl, SymbolTable table) {
        String var = varDecl.get("name");
        JmmNode type = varDecl.getChildren(Kind.TYPE).get(0);
        String typeName = type.get("name");

        if (!typeName.equals("int") && !typeName.equals("boolean") && !typeName.equals("String") && !typeName.equals(table.getClassName())) {
            if (table.getImports().stream().noneMatch(importName -> {
                String[] imports = importName.split("\\.");
                return imports[imports.length - 1].equals(typeName);
            })) {
                var message = String.format("No class imported to match variable %s with type %s.", var, typeName);
                addReport(Report.newError(
                        Stage.SEMANTIC,
                        NodeUtils.getLine(varDecl),
                        NodeUtils.getColumn(varDecl),
                        message,
                        null)
                );
            }
        }

        return null;
    }

    private Void visitThisExpr(JmmNode thisExpr, SymbolTable table) {
        if (currentMethod.equals("main")) {
            // Create error report
            var message = "\"this\" expression cannot be used in a static method.";
            addReport(Report.newError(
                    Stage.SEMANTIC,
                    NodeUtils.getLine(thisExpr),
                    NodeUtils.getColumn(thisExpr),
                    message,
                    null)
            );
        }

        return null;
    }

    private Void visitVarRefExpr(JmmNode varRefExpr, SymbolTable table) {
        SpecsCheck.checkNotNull(currentMethod, () -> "Expected current method to be set");

        // Check if exists a parameter or variable declaration with the same name as the variable reference
        var varRefName = varRefExpr.get("name");
        Type varType = TypeUtils.getExprType(varRefExpr, table);

        if (varType != null && varType.hasAttribute("isClass")) return null;
        /*// Var is an imported class, return
        if (table.getImports().stream()
                .anyMatch(importName -> {
                    String[] imports = importName.split("\\.");
                    return imports[imports.length - 1].equals(varRefName);
                })) {
            return null;
        }
        // Var is the class
        if (varRefName.equals(table.getClassName())) return null;*/

        // Var is a parameter, return
        if (table.getParameters(currentMethod).stream()
                .anyMatch(param -> param.getName().equals(varRefName))) {
            return null;
        }

        // Var is a declared local variable, return
        if (table.getLocalVariables(currentMethod).stream()
                .anyMatch(varDecl -> varDecl.getName().equals(varRefName))) {
            return null;
        }

        // Var is a field, return
        if (table.getFields().stream()
                .anyMatch(field -> field.getName().equals(varRefName))) {
            if (currentMethod.equals("main")) {
                // Create error report
                var message = String.format("Cannot have a field %s in a static method.", varRefName);
                addReport(Report.newError(
                        Stage.SEMANTIC,
                        NodeUtils.getLine(varRefExpr),
                        NodeUtils.getColumn(varRefExpr),
                        message,
                        null)
                );
            }

            return null;
        }

        // Create error report
        var message = String.format("Variable or Class '%s' does not exist.", varRefName);
        addReport(Report.newError(
                Stage.SEMANTIC,
                NodeUtils.getLine(varRefExpr),
                NodeUtils.getColumn(varRefExpr),
                message,
                null)
        );

        return null;
    }

}
