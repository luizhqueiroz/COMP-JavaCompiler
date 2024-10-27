package pt.up.fe.comp2024.optimization;

import pt.up.fe.comp.jmm.analysis.table.SymbolTable;
import pt.up.fe.comp.jmm.analysis.table.Type;
import pt.up.fe.comp.jmm.ast.AJmmVisitor;
import pt.up.fe.comp.jmm.ast.JmmNode;
import pt.up.fe.comp.jmm.ollir.OllirUtils;
import pt.up.fe.comp2024.ast.Kind;
import pt.up.fe.comp2024.ast.TypeUtils;

import java.util.List;

import static pt.up.fe.comp2024.ast.Kind.*;

/**
 * Generates OLLIR code from JmmNodes that are expressions.
 */
public class OllirExprGeneratorVisitor extends AJmmVisitor<Void, OllirExprResult> {

    private static final String SPACE = " ";
    private static final String ASSIGN = ":=";
    private final String END_STMT = ";\n";

    private final SymbolTable table;

    private int NEXT_IF = -1;

    public OllirExprGeneratorVisitor(SymbolTable table) {
        this.table = table;
    }

    @Override
    protected void buildVisitor() {
        addVisit(VAR_REF_EXPR, this::visitVarRef);
        addVisit(BINARY_EXPR, this::visitBinExpr);
        addVisit(INTEGER_LITERAL, this::visitInteger);
        addVisit(BOOLEAN_LITERAL, this::visitBool);
        addVisit(METHOD_CALL_EXPR, this::visitMethodCall);
        addVisit(THIS_EXPR, this::visitThis);
        addVisit(NEW_OBJECT_EXPR, this::visitNewObject);
        addVisit(PAREN_EXPR, this::visitParenExpr);
        addVisit(NEW_INT_ARRAY_EXPR, this::visitNewIntArray);
        addVisit(ARRAY_ACCESS_EXPR, this::visitArrayAccessExpr);
        addVisit(ARRAY_LENGTH_EXPR, this::visitArrayLengthExpr);
        addVisit(ARRAY_EXPR, this::visitArrayExpr);
        addVisit(NOT_EXPR, this::visitNotExpr);
        setDefaultVisit(this::defaultVisit);
    }


    private OllirExprResult visitInteger(JmmNode node, Void unused) {
        var intType = new Type(TypeUtils.getIntTypeName(), false);
        String ollirIntType = OptUtils.toOllirType(intType);
        String code = node.get("value") + ollirIntType;
        return new OllirExprResult(code);
    }

    private OllirExprResult visitBool(JmmNode node, Void unused) {
        var boolType = new Type("boolean", false);
        String ollirBoolType = OptUtils.toOllirType(boolType);
        String value;
        if (node.get("value").equals("false")){
            value="0";
        }
        else{
            value="1";
        }
        String code = value + ollirBoolType;
        return new OllirExprResult(code);
    }


    private OllirExprResult visitBinExpr(JmmNode node, Void unused) {

        if(node.get("op").equals("&&") ){
            return visitAndBoolExpr(node, unused);
        }

        if(node.get("op").equals("<")){
            return visitLessThanExpr(node, unused);
        }

        var lhs = visit(node.getJmmChild(0));
        var rhs = visit(node.getJmmChild(1));

        StringBuilder computation = new StringBuilder();

        // code to compute the children
        computation.append(lhs.getComputation());
        computation.append(rhs.getComputation());

        // code to compute self
        Type resType = TypeUtils.getExprType(node, table);
        String resOllirType = OptUtils.toOllirType(resType);
        String code = OptUtils.getTemp() + resOllirType;

        computation.append(code).append(SPACE)
                .append(ASSIGN).append(resOllirType).append(SPACE)
                .append(lhs.getCode()).append(SPACE);

        Type type = TypeUtils.getExprType(node, table);
        computation.append(node.get("op")).append(OptUtils.toOllirType(type)).append(SPACE)
                .append(rhs.getCode()).append(END_STMT);

        return new OllirExprResult(code, computation);
    }

    private OllirExprResult visitAndBoolExpr(JmmNode node, Void unused){
        var lhs = visit(node.getJmmChild(0));
        var rhs = visit(node.getJmmChild(1));

        StringBuilder computation = new StringBuilder();
        StringBuilder code = new StringBuilder();

        var next = getNextIf();

        var temp = OptUtils.getTemp();
        var boolType = TypeUtils.getBoolType();
        var ollirType = OptUtils.toOllirType(boolType);

        code.append(temp + ollirType);

        computation.append(lhs.getComputation());
        computation.append("if(" + lhs.getCode() + ") goto true_" + next + ";\n");
        computation.append(code + SPACE + ASSIGN + ollirType + SPACE + "0" + ollirType + END_STMT);
        computation.append("goto end_" + next + ";\n");

        computation.append("true_" + next + ":\n");
        computation.append(rhs.getComputation());
        computation.append(code + SPACE + ASSIGN + ollirType + SPACE + rhs.getCode() + END_STMT);
        computation.append("end_" + next + ":\n");

        return new OllirExprResult(code.toString(), computation);

    }

    private int getNextIf(){
        return ++NEXT_IF;
    }

    private OllirExprResult visitLessThanExpr(JmmNode node, Void unused){
        var lhs = visit(node.getJmmChild(0));
        var rhs = visit(node.getJmmChild(1));

        StringBuilder computation = new StringBuilder();
        StringBuilder code = new StringBuilder();

        var next = getNextIf();

        var temp = OptUtils.getTemp();
        var boolType = TypeUtils.getBoolType();
        var ollirType = OptUtils.toOllirType(boolType);

        code.append(temp + ollirType);

        computation.append(lhs.getComputation());
        computation.append(rhs.getComputation());
        computation.append("if(" + lhs.getCode() + " <.bool " + rhs.getCode() + ") goto true_" + next + ";\n");
        computation.append(code + SPACE + ASSIGN + ollirType + SPACE + "0" + ollirType + END_STMT);
        computation.append("goto end_" + next + ";\n");

        computation.append("true_" + next + ":\n");
        computation.append(code + SPACE + ASSIGN + ollirType + SPACE + "1" + ollirType + END_STMT);
        computation.append("end_" + next + ":\n");

        return new OllirExprResult(code.toString(), computation);
    }

    private OllirExprResult visitVarRef(JmmNode node, Void unused) {
        StringBuilder comp = new StringBuilder();
        StringBuilder code = new StringBuilder();

        var id = node.get("name");

        Type type = TypeUtils.getExprType(node, table);
        String ollirType = OptUtils.toOllirType(type);

        if (isField(id) && !isLocal(id, getMethod(node))){
            var temp = OptUtils.getTemp();

            code.append(temp + ollirType);

            comp.append(code).append(SPACE)
                    .append(ASSIGN).append(ollirType).append(SPACE).append("getfield(this, ")
                    .append(id).append(ollirType).append(")").append(ollirType).append(END_STMT);

            return new OllirExprResult(code.toString(), comp);
        }
        else{
            code.append(id + ollirType);
        }

        return new OllirExprResult(code.toString(), comp);
    }

    private OllirExprResult visitThis(JmmNode node, Void unused) {
        StringBuilder code = new StringBuilder();
        StringBuilder computation = new StringBuilder();

        var id = "this";
        Type type = TypeUtils.getExprType(node, table);
        String ollirType = OptUtils.toOllirType(type);

        code.append(id + ollirType);

        return new OllirExprResult(code.toString(), computation);
    }

    private OllirExprResult visitMethodCall(JmmNode node, Void unused){
        var parentKind = getKindFromString(node.getJmmParent().getKind());

        var method = getMethod(node);

        if(checkStatic(node, method)){
            return visitStaticMethodCall(node, parentKind);
        }
        return visitVirtualMethodCall(node, parentKind);
    }

    private OllirExprResult visitStaticMethodCall(JmmNode node, Kind kind) {

        StringBuilder code = new StringBuilder();
        StringBuilder computation = new StringBuilder();
        StringBuilder lastComputation = new StringBuilder();

        var methodName = node.get("name");

        switch (kind){
            case EXPR_STMT ->{
                var child = node.getJmmChild(0);
                lastComputation.append("invokestatic(" + child.get("name") + ", \"" + methodName + "\"");
                computation.append(getArgumentsComputation(node, lastComputation));

                if (child.hasAttribute("name")) {
                    var returnType = table.getReturnTypeTry(methodName);
                    if (isImported(child.get("name"))) {
                        computation.append(".V" + END_STMT);
                    } else if (returnType.isPresent()) {
                        computation.append(OptUtils.toOllirType(returnType.get()) + END_STMT);
                    }else{
                        computation.append(".V" + END_STMT);
                    }
                }

            }
            case ASSIGN_STMT -> {
                var child = node.getJmmChild(0);
                var varName = node.getJmmParent().get("name");
                var type = findType(varName, getMethod(node));
                lastComputation.append("invokestatic(" + child.get("name")  + ", \"" + methodName + "\"");

                var args = getArgumentsComputationSpecial(node, lastComputation, type);
                computation.append(args.getComputation());
                code.append(args.getCode());
            }
            case RETURN_STMT -> {
                var child = node.getJmmChild(0);
                var methodType = node.getAncestor(Kind.METHOD_DECL).get().getChild(0).get("name");
                var type = new Type(methodType, false);

                lastComputation.append("invokestatic(" + child.get("name")  + ", \"" + methodName + "\"");
                var args = getArgumentsComputationSpecial(node, lastComputation, type);
                computation.append(args.getComputation());
                code.append(args.getCode());
            }
            case BINARY_EXPR -> {
                var parent = node.getJmmParent();
                var type = getTypeByOp(parent.get("op"));
                var child = node.getJmmChild(0);
                lastComputation.append("invokestatic(" + child.get("name")  + ", \"" + methodName + "\"");
                var args = getArgumentsComputationSpecial(node, lastComputation, type);
                computation.append(args.getComputation());
                code.append(args.getCode());
            }
            case METHOD_CALL_EXPR -> {
                var child = node.getJmmChild(0);
                var parent = node.getJmmParent();
                var allChilds = parent.getChildren();
                int i=0;
                for (var achild : allChilds){
                    if (achild.equals(child)){
                        break;
                    }
                    i++;
                }
                lastComputation.append("invokestatic(" + child.get("name")  + ", \"" + methodName + "\"");
                if (i==0){
                    var args = getArgumentsComputationSpecial(node, lastComputation, new Type(child.get("name"), false));
                    computation.append(args.getComputation());
                    code.append(args.getCode());
                }
                else{
                    var type = table.getParameters(parent.get("name")).get(i-1).getType();

                    var args = getArgumentsComputationSpecial(node, lastComputation, type);
                    computation.append(args.getComputation());
                    code.append(args.getCode());
                }
            }
            case PAREN_EXPR -> {
                var child = node.getJmmChild(0);
                lastComputation.append("invokestatic(" + child.get("name")  + ", \"" + methodName + "\"");
                var type = table.getReturnType(methodName);
                var args = getArgumentsComputationSpecial(node, lastComputation, type);
                computation.append(args.getComputation());
                code.append(args.getCode());
            }
            case ARRAY_LENGTH_EXPR -> {
                var child = node.getJmmChild(0);
                lastComputation.append("invokestatic(" + child.get("name")  + ", \"" + methodName + "\"");
                Type arrayType = TypeUtils.getIntArrayType();
                var args = getArgumentsComputationSpecial(node, lastComputation, arrayType);
                computation.append(args.getComputation());
                code.append(args.getCode());
            }
            case ARRAY_ACCESS_EXPR, ARRAY_ASSIGN_STMT, ARRAY_EXPR, NEW_INT_ARRAY_EXPR -> {
                var child = node.getJmmChild(0);
                lastComputation.append("invokestatic(" + child.get("name")  + ", \"" + methodName + "\"");
                Type intType = TypeUtils.getIntType();
                var args = getArgumentsComputationSpecial(node, lastComputation, intType);
                computation.append(args.getComputation());
                code.append(args.getCode());
            }
            case WHILE_STMT, IF_STMT, NOT_EXPR -> {
                var child = node.getJmmChild(0);
                lastComputation.append("invokestatic(" + child.get("name")  + ", \"" + methodName + "\"");
                Type boolType = TypeUtils.getBoolType();
                var args = getArgumentsComputationSpecial(node, lastComputation, boolType);
                computation.append(args.getComputation());
                code.append(args.getCode());
            }
        }
        return new OllirExprResult(code.toString(), computation);
    }

    private OllirExprResult visitVirtualMethodCall(JmmNode node, Kind kind){
        StringBuilder code = new StringBuilder();
        StringBuilder computation = new StringBuilder();
        StringBuilder lastComputation = new StringBuilder();

        var methodName = node.get("name");

        switch (kind){
            case EXPR_STMT ->{
                var child = node.getJmmChild(0);
                var childCode = visit(child);
                lastComputation.append("invokevirtual(" + childCode.getCode()  + ", \"" + methodName + "\"");
                computation.append(childCode.getComputation() + getArgumentsComputation(node, lastComputation));

                if (child.hasAttribute("name")) {
                    var varType = TypeUtils.getExprType(child, table);
                    var returnType = table.getReturnTypeTry(methodName);
                    if (isImported(varType.getName())) {
                        computation.append(".V" + END_STMT);
                    } else if (returnType.isPresent()) {
                        computation.append(OptUtils.toOllirType(returnType.get()) + END_STMT);
                    }else{
                        computation.append(".V" + END_STMT);
                    }
                }
                else{
                    String type = "";
                    try{
                        type = TypeUtils.getExprType(child, table).getName();
                    }
                    catch (Exception e){
                        // do nothing ;
                    }
                    if (isMethod(methodName)){
                        type = OptUtils.toOllirType(table.getReturnType(methodName));
                    }
                    else{
                        type = ".V";
                    }
                    computation.append(type + END_STMT);
                }
            }
            case ASSIGN_STMT -> {
                var child = node.getJmmChild(0);
                var varName = node.getJmmParent().get("name");
                var type = findType(varName, getMethod(node));
                var childCode = visit(child);
                lastComputation.append("invokevirtual(" + childCode.getCode()  + ", \"" + methodName + "\"");
                var args = getArgumentsComputationSpecial(node, lastComputation, type);
                computation.append(childCode.getComputation() + args.getComputation());
                code.append(args.getCode());
            }
            case RETURN_STMT -> {
                var child = node.getJmmChild(0);
                var methodType = node.getAncestor(Kind.METHOD_DECL).get().getChild(0).get("name");
                var type = new Type(methodType, false);
                var childCode = visit(child);
                lastComputation.append("invokevirtual(" + childCode.getCode()  + ", \"" + methodName + "\"");
                var args = getArgumentsComputationSpecial(node, lastComputation, type);
                computation.append(childCode.getComputation() + args.getComputation());
                code.append(args.getCode());
            }
            case BINARY_EXPR -> {
                var parent = node.getJmmParent();
                var type = getTypeByOp(parent.get("op"));
                var child = node.getJmmChild(0);
                var childCode = visit(child);
                lastComputation.append("invokevirtual(" + childCode.getCode()  + ", \"" + methodName + "\"");
                var args = getArgumentsComputationSpecial(node, lastComputation, type);
                computation.append(childCode.getComputation() + args.getComputation());
                code.append(args.getCode());
            }
            case METHOD_CALL_EXPR -> {
                var child = node.getJmmChild(0);
                var parent = node.getJmmParent();
                var allChilds = parent.getChildren();
                int i=0;
                for (var achild : allChilds){
                    if (achild.equals(child)){
                        break;
                    }
                    i++;
                }
                var childCode = visit(child);
                lastComputation.append("invokevirtual(" + childCode.getCode()  + ", \"" + methodName + "\"");
                if (i==0){
                    var type = table.getReturnTypeTry(methodName);
                    if (type.isPresent()){
                        var args = getArgumentsComputationSpecial(node, lastComputation, type.get());
                        computation.append(childCode.getComputation() + args.getComputation());
                        code.append(args.getCode());
                    }
                    else{
                        var parentType = table.getReturnType(parent.get("name"));
                        var args = getArgumentsComputationSpecial(node, lastComputation, parentType);
                        computation.append(childCode.getComputation() + args.getComputation());
                        code.append(args.getCode());
                    }
                }
                else{
                    var type = table.getReturnTypeTry(methodName);
                    if (type.isPresent()){
                        var args = getArgumentsComputationSpecial(node, lastComputation, type.get());
                        computation.append(childCode.getComputation() + args.getComputation());
                        code.append(args.getCode());
                    }
                    else{
                        var type2 = table.getParameters(parent.get("name")).get(i-1).getType();
                        var args = getArgumentsComputationSpecial(node, lastComputation, type2);
                        computation.append(childCode.getComputation() + args.getComputation());
                        code.append(args.getCode());
                    }
                }
            }
            case PAREN_EXPR -> {
                var child = node.getJmmChild(0);
                var childCode = visit(child);
                lastComputation.append("invokevirtual(" + childCode.getCode()  + ", \"" + methodName + "\"");
                var type = table.getReturnType(methodName);
                var args = getArgumentsComputationSpecial(node, lastComputation, type);
                computation.append(childCode.getComputation() + args.getComputation());
                code.append(args.getCode());
            }
            case ARRAY_LENGTH_EXPR -> {
                var child = node.getJmmChild(0);
                var childCode = visit(child);
                lastComputation.append("invokevirtual(" + childCode.getCode()  + ", \"" + methodName + "\"");
                Type arrayType = TypeUtils.getIntArrayType();
                var args = getArgumentsComputationSpecial(node, lastComputation, arrayType);
                computation.append(childCode.getComputation() + args.getComputation());
                code.append(args.getCode());
            }
            case ARRAY_ACCESS_EXPR, ARRAY_ASSIGN_STMT, NEW_INT_ARRAY_EXPR, ARRAY_EXPR -> {
                var child = node.getJmmChild(0);
                var childCode = visit(child);
                lastComputation.append("invokevirtual(" + childCode.getCode()  + ", \"" + methodName + "\"");
                Type intType = TypeUtils.getIntType();
                var args = getArgumentsComputationSpecial(node, lastComputation, intType);
                computation.append(childCode.getComputation() + args.getComputation());
                code.append(args.getCode());
            }
            case WHILE_STMT, IF_STMT, NOT_EXPR -> {
                var child = node.getJmmChild(0);
                var childCode = visit(child);

                lastComputation.append("invokevirtual(" + childCode.getCode()  + ", \"" + methodName + "\"");
                Type boolType = TypeUtils.getBoolType();

                var args = getArgumentsComputationSpecial(node, lastComputation, boolType);
                computation.append(childCode.getComputation() + args.getComputation());
                code.append(args.getCode());

            }
        }
        return new OllirExprResult(code.toString(), computation);
    }

    private OllirExprResult visitNewObject(JmmNode node, Void unused) {

        var id = node.get("name");
        var args = node.getChildren();
        StringBuilder computation = new StringBuilder();
        StringBuilder code = new StringBuilder();

        for (int i=1; i<args.size(); i++) {
            var argResult = visit(args.get(i));

            code.append(argResult.getComputation());
        }

        var temp = OptUtils.getTemp();

        code.append(temp).append(".").append(id).append(SPACE)
                .append(ASSIGN).append(".").append(id).append(SPACE)
                .append("new")
                .append("(").append(id);

        computation.append(temp).append(".").append(id);


        for (int i=1; i<args.size(); i++) {
            code.append(", ");
            var argResult = visit(args.get(i));
            code.append(argResult.getCode());
        }

        code.append(")").append(".").append(id).append(END_STMT);
        code.append("invokespecial(").append(temp).append(".").append(id).append(", \"<init>\").V;\n");

        return new OllirExprResult(computation.toString(), code);
    }

    private OllirExprResult visitNewIntArray(JmmNode node, Void unused) {
        var child = visit(node.getJmmChild(0));
        StringBuilder computation = new StringBuilder();
        computation.append(child.getComputation());

        var tempChild = OptUtils.getTemp();
        var arrayType = OptUtils.toOllirType((TypeUtils.getIntArrayType()));
        var childType = OptUtils.toOllirType(TypeUtils.getIntType());

        var childAux = tempChild + childType;
        computation.append(childAux + SPACE + ASSIGN + childType + SPACE + child.getCode() + END_STMT);

        StringBuilder code = new StringBuilder();
        var temp = OptUtils.getTemp();
        code.append(temp + arrayType);
        computation.append(code + SPACE + ASSIGN + arrayType + SPACE + "new(array, " + childAux + ")" + arrayType + END_STMT);

        return new OllirExprResult(code.toString(), computation);
    }

    private OllirExprResult visitArrayAccessExpr(JmmNode node, Void unused) {
        StringBuilder code = new StringBuilder();
        StringBuilder computation = new StringBuilder();

        var first = visit(node.getJmmChild(0));
        var second = visit(node.getJmmChild(1));

        computation.append(first.getComputation());
        computation.append(second.getComputation());

        var temp = OptUtils.getTemp();
        var type = TypeUtils.getExprType(node, table);
        var ollirType = OptUtils.toOllirType(type);

        code.append(temp + ollirType);
        computation.append(code + SPACE + ASSIGN + ollirType + SPACE + first.getCode() + "[" + second.getCode()  + "]" + ollirType + END_STMT);


        return new OllirExprResult(code.toString(), computation);
    }

    private OllirExprResult visitArrayLengthExpr(JmmNode node, Void unused) {
        StringBuilder code = new StringBuilder();
        StringBuilder computation = new StringBuilder();

        var child = node.getChild(0);
        var visitChild = visit(child);

        //if(isField(child.get("name")) && !isLocal(child.get("name"), getMethod(node))){
            computation.append(visitChild.getComputation());
        //}

        var temp = OptUtils.getTemp();
        var type = TypeUtils.getIntType();
        var ollirType = OptUtils.toOllirType(type);

        code.append(temp + ollirType);

        computation.append(code + SPACE + ASSIGN + ollirType + SPACE + "arraylength(" + visitChild.getCode() + ")" + ollirType + END_STMT);

        return new OllirExprResult(code.toString(), computation);
    }

    private OllirExprResult visitNotExpr(JmmNode node, Void unused) {
        StringBuilder code = new StringBuilder();
        StringBuilder computation = new StringBuilder();

        var child = visit(node.getJmmChild(0));
        computation.append(child.getComputation());

        var temp = OptUtils.getTemp();
        var type = TypeUtils.getBoolType();
        var ollirType = OptUtils.toOllirType(type);

        code.append(temp + ollirType);
        computation.append(code + SPACE + ASSIGN + ollirType + SPACE + "!" + ollirType + SPACE + child.getCode() + END_STMT);
        System.out.println("COMPUTATION: " + computation);

        return new OllirExprResult(code.toString(), computation);
    }

    private OllirExprResult visitArrayExpr(JmmNode node, Void unused){
        StringBuilder code = new StringBuilder();
        StringBuilder computation = new StringBuilder();

        var length = node.getNumChildren();
        var temp = OptUtils.getTemp();
        var arrayType = TypeUtils.getIntArrayType();
        var ollirType = OptUtils.toOllirType(arrayType);

        code.append(temp + ollirType);
        computation.append(code + SPACE + ASSIGN + ollirType + SPACE + "new(array, " + length + ".i32)" + ollirType + END_STMT);

        for (int i=0; i<length; i++){
            var childResult = visit(node.getChild(i));
            computation.append(childResult.getComputation());
            computation.append(code + "[" + i + ".i32].i32" + SPACE + ASSIGN + ".i32" + SPACE + childResult.getCode() + END_STMT);
        }

        return new OllirExprResult(code.toString(), computation);
    }

    //AUXILIAR FUNCTIONS

    // checks if a given name is a class from imports or the class itself
    private Boolean isClass(String name, String method){
        if ((isField(name) && (!method.equals("main")))|| isLocal(name, method)) return false;

        return isImported(name) || table.getClassName().equals(name);
    }

    // from a string get the corresponding Kind
    private Kind getKindFromString (String kind){
        return switch (kind){
            case "ExprStmt" -> EXPR_STMT;
            case "AssignStmt" -> ASSIGN_STMT;
            case "ReturnStmt" -> RETURN_STMT;
            case "MethodCallExpr" -> METHOD_CALL_EXPR;
            case "ThisExpr" -> THIS_EXPR;
            case "VarRefExpr" -> VAR_REF_EXPR;
            case "BinaryExpr" -> BINARY_EXPR;
            case "NewObjectExpr" -> NEW_OBJECT_EXPR;
            case "ArrayExpr" -> ARRAY_EXPR;
            case "ArrayLengthExpr" -> ARRAY_LENGTH_EXPR;
            case "NewIntArrayExpr" -> NEW_INT_ARRAY_EXPR;
            case "ParenExpr" -> PAREN_EXPR;
            case "NotExpr" -> NOT_EXPR;
            case "ArrayAccessExpr" -> ARRAY_ACCESS_EXPR;
            case "WhileStmt" -> WHILE_STMT;
            case "IfStmt" -> IF_STMT;
            case "ArrayAssignStmt" -> ARRAY_ASSIGN_STMT;
            default -> null;
        };
    }

    // check if a given method call is static
    private boolean checkStatic(JmmNode node, String method){
        var firstChild = node.getChild(0);
        return switch (firstChild.getKind()){
            case "VarRefExpr" -> isClass(firstChild.get("name"), method);
            case "ParenExpr", "MethodCallExpr" -> checkStatic(firstChild, method);
            default -> false;
        };
    }

    // returns the type of a binary operation: bool or integer
    private Type getTypeByOp (String op){
        return switch (op) {
            case "+", "-", "*", "/", "<", ">" -> new Type(TypeUtils.getIntTypeName(), false);
            default -> new Type("boolean", false);
        };
    }

    // gets the arguments of a given method call
    private StringBuilder getArgumentsComputation (JmmNode node, StringBuilder lastComputation){
        StringBuilder computation = new StringBuilder();
        var args = node.getChildren();
        for (int i=1; i<args.size(); i++){
            var auxi = visit(args.get(i));
            lastComputation.append(", " + auxi.getCode());

            if (args.get(i).hasAttribute("name")) {
                if (isField(args.get(i).get("name")) && !isLocal(args.get(i).get("name"), getMethod(node))) {
                    computation.append(auxi.getComputation());
                    continue;
                }
            }
            computation.append(auxi.getComputation());
        }
        computation.append(lastComputation + ")");
        return computation;
    }

    // gets the arguments of a given method call
    // this is special because it appends the temp and type to the computation and returns the new code updated
    private OllirExprResult getArgumentsComputationSpecial (JmmNode node, StringBuilder lastComputation, Type type){
        StringBuilder computation = new StringBuilder();
        var args = node.getChildren();
        for (int i=1; i<args.size(); i++){
            var auxi = visit(args.get(i));
            lastComputation.append(", " + auxi.getCode());

            if (args.get(i).hasAttribute("name")) {
                if (isField(args.get(i).get("name")) && !isLocal(args.get(i).get("name"), getMethod(node))) {
                    computation.append(auxi.getComputation());
                    continue;
                }
            }
            computation.append(auxi.getComputation());
        }
        var temp = OptUtils.getTemp();
        var tempType = OptUtils.toOllirType(type);
        StringBuilder code = new StringBuilder();
        code.append(temp + tempType);
        computation.append(temp + tempType + SPACE + ASSIGN + tempType + SPACE + lastComputation + ")" + tempType + END_STMT);
        return new OllirExprResult(code.toString(), computation);
    }

    private OllirExprResult visitParenExpr(JmmNode node, Void unused) {
        var child = visit(node.getJmmChild(0));
        return new OllirExprResult(child.getCode(), child.getComputation());
    }

    // checks if a given name is coming from the imports
    // checks only what comes after the last "."
    private Boolean isImported(String name){
        List<String> imports = table.getImports();
        
        for (String imp : imports){

            int lastIndex = imp.lastIndexOf(".");

            String result;
            if (lastIndex != -1) {
                result = imp.substring(lastIndex + 1);
            } else {
                result = imp;
            }
            if (result.equals(name)){
                return true;
            }
        }
        return false;
    }

    // checks if a given name is a field of the class
    private Boolean isField(String name){
        for (var field : table.getFields()){
            if (field.getName().equals(name)){
                return true;
            }
        }
        return false;
    }

    // checks if a given name is a local variable
    private Boolean isLocal(String name, String method){
        for (var local : table.getLocalVariables(method)){
            if (local.getName().equals(name)){
                return true;
            }
        }

        for (var param : table.getParameters(method)){
            if (param.getName().equals(name)){
                return true;
            }
        }
        return false;
    }

    // returns the method name of a given node
    private String getMethod(JmmNode node){
        var currentNode = node;
        if (currentNode == null){
            return "";
        }
        while(!currentNode.getKind().equals(Kind.METHOD_DECL.toString()) && !currentNode.getKind().equals(Kind.MAIN_METHOD_DECL.toString())){
            currentNode = currentNode.getJmmParent();
            if (currentNode == null){
                return "";
            }
        }
        return currentNode.get("name");
    }

    // returns the type of a given name
    // first checks the params, then locals and fields in the end
    private Type findType(String name, String method){

        for (var param: table.getParameters(method)){
            if (param.getName().equals(name)){
                return (param.getType());
            }
        }

        for (var local : table.getLocalVariables(method)){
            if (local.getName().equals(name)){
                return (local.getType());
            }
        }

        for (var field : table.getFields()){
            if (field.getName().equals(name)){
                return (field.getType());
            }
        }

        return null;
    }

    // checks if a given name is a method of the class
    private Boolean isMethod(String name){
        for (var method : table.getMethods()){
            if (method.equals(name)){
                return true;
            }
        }
        return false;
    }

    /**
     * Default visitor. Visits every child node and return an empty result.
     *
     * @param node
     * @param unused
     * @return
     */
    private OllirExprResult defaultVisit(JmmNode node, Void unused) {

        for (var child : node.getChildren()) {
            visit(child);
        }

        return OllirExprResult.EMPTY;
    }

}
