package pt.up.fe.comp2024.ast;

import pt.up.fe.comp.jmm.analysis.table.Symbol;
import pt.up.fe.comp.jmm.analysis.table.SymbolTable;
import pt.up.fe.comp.jmm.analysis.table.Type;
import pt.up.fe.comp.jmm.ast.JmmNode;


public class TypeUtils {

    private static final String INT_TYPE_NAME = "int";
    private static final String BOOL_TYPE_NAME = "boolean";

    public static String getIntTypeName() {
        return INT_TYPE_NAME;
    }

    public static Type getIntType() {
        return new Type(INT_TYPE_NAME, false);
    }

    public static Type getBoolType() {
        return new Type(BOOL_TYPE_NAME, false);
    }

    public static Type getIntArrayType() {
        return new Type(INT_TYPE_NAME, true);
    }

    /**
     * Gets the {@link Type} of an arbitrary expression.
     *
     * @param expr
     * @param table
     * @return
     */
    public static Type getExprType(JmmNode expr, SymbolTable table) {
        // TODO: Simple implementation that needs to be expanded

        var kind = Kind.fromString(expr.getKind());

        Type type = switch (kind) {
            case BINARY_EXPR -> getBinExprType(expr);
            case VAR_REF_EXPR -> getVarExprType(expr, table);
            case INTEGER_LITERAL, ARRAY_LENGTH_EXPR -> getIntType();
            case BOOLEAN_LITERAL -> getBoolType();
            case NEW_OBJECT_EXPR -> getNewObjectExprType(expr, table);
            case NEW_INT_ARRAY_EXPR -> getNewIntArrayExprType(expr, table);
            case ARRAY_EXPR -> getIntArrayType();
            case METHOD_CALL_EXPR -> getMethodCallExprType(expr, table);
            case ARRAY_ACCESS_EXPR -> getArrayAccessExprType(expr, table);
            case THIS_EXPR -> getThisExprType(table);
            case PAREN_EXPR -> getParenExprType(expr, table);
            case NOT_EXPR -> getNotExprType(expr, table);
            default -> throw new UnsupportedOperationException("Can't compute type for expression kind '" + kind + "'");
        };

        return type;
    }

    private static Type getNotExprType(JmmNode notExpr, SymbolTable table) {
        JmmNode child = notExpr.getChild(0);

        return getExprType(child, table);
    }

    private static Type getParenExprType(JmmNode parenExpr, SymbolTable table) {
        JmmNode child = parenExpr.getChild(0);

        return getExprType(child, table);
    }

    private static Type getThisExprType(SymbolTable table) {
        return new Type(table.getClassName(), false);
    }

    private static Type getNewIntArrayExprType(JmmNode arrayAccessExpr, SymbolTable table) {
        return getIntArrayType();
    }

    private static Type getArrayAccessExprType(JmmNode arrayAccessExpr, SymbolTable table) {
        JmmNode expr = arrayAccessExpr.getChild(0);
        Type exprType = getExprType(expr, table);

        if (exprType == null) return null;

        return new Type(exprType.getName(), false);
    }

    private static Type getMethodCallExprType(JmmNode methodCallExpr, SymbolTable table) {
        // TODO: Simple implementation that needs to be expanded
        String methodCallName = methodCallExpr.get("name");

        return table.getReturnType(methodCallName);
    }

    private static Type getNewObjectExprType(JmmNode newObjectExpr, SymbolTable table) {
        String classRefName = newObjectExpr.get("name");

        String mainClassName = table.getClassName();
        if (classRefName.equals(mainClassName))
            return new Type(mainClassName, false);

        String superClassName = table.getSuper();
        if (classRefName.equals(superClassName)) {
            return new Type(classRefName, false);
        }

        for (String importName : table.getImports()) {
            String[] imports = importName.split("\\.");
            if (imports[imports.length - 1].equals(classRefName))
                return new Type(classRefName, false);
        }

        return null;
    }

    private static Type getBinExprType(JmmNode binaryExpr) {
        // TODO: Simple implementation that needs to be expanded

        String operator = binaryExpr.get("op");

        return switch (operator) {
            case "+", "*", "-", "/" -> getIntType();
            case "!", "&&", "<" -> getBoolType();
            default ->
                    throw new RuntimeException("Unknown operator '" + operator + "' of expression '" + binaryExpr + "'");
        };
    }


    private static Type getVarExprType(JmmNode varRefExpr, SymbolTable table) {
        // TODO: Simple implementation that needs to be expanded
        String varRefName = varRefExpr.get("name");

        JmmNode parent = varRefExpr.getJmmParent();
        while (!parent.getKind().equals(Kind.METHOD_DECL.toString()) && !parent.getKind().equals(Kind.MAIN_METHOD_DECL.toString())) {
            parent = parent.getJmmParent();
        }

        for (Symbol local : table.getLocalVariables(parent.get("name"))) {
            if (local.getName().equals(varRefName))
                return local.getType();
        }

        for (Symbol param : table.getParameters(parent.get("name"))) {
            if (param.getName().equals(varRefName))
                return param.getType();
        }

        for (Symbol field : table.getFields()) {
            if (field.getName().equals(varRefName))
                return field.getType();
        }

        if (table.getImports().stream()
                .anyMatch(importName -> {
                    String[] imports = importName.split("\\.");
                    return imports[imports.length - 1].equals(varRefName);
                }) || varRefName.equals(table.getClassName())) {
            Type type = new Type(varRefName, false);
            type.putObject("isClass", true);
            return type;
        }

        return null;
        //throw new RuntimeException("Unknown var '" + varRefName + "'");
    }


    /**
     * @param sourceType
     * @param destinationType
     * @return true if sourceType can be assigned to destinationType
     */
    public static boolean areTypesAssignable(Type sourceType, Type destinationType) {
        // TODO: Simple implementation that needs to be expanded
        return sourceType.equals(destinationType);
    }
}
