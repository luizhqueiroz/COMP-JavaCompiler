package pt.up.fe.comp2024.optimization;

import pt.up.fe.comp.jmm.analysis.table.SymbolTable;
import pt.up.fe.comp.jmm.analysis.table.Type;
import pt.up.fe.comp.jmm.ast.AJmmVisitor;
import pt.up.fe.comp.jmm.ast.JmmNode;
import pt.up.fe.comp2024.ast.Kind;
import pt.up.fe.comp2024.ast.NodeUtils;
import pt.up.fe.comp2024.ast.TypeUtils;

import java.util.ArrayList;
import java.util.List;

import static pt.up.fe.comp2024.ast.Kind.*;

/**
 * Generates OLLIR code from JmmNodes that are not expressions.
 */
public class OllirGeneratorVisitor extends AJmmVisitor<Void, String> {

    private static final String SPACE = " ";
    private static final String ASSIGN = ":=";
    private final String END_STMT = ";\n";
    private final String NL = "\n";
    private final String L_BRACKET = " {\n";
    private final String R_BRACKET = "}\n";
    private int NEXT_IF = -1;
    private int NEXT_WHILE = -1;


    private final SymbolTable table;

    private final OllirExprGeneratorVisitor exprVisitor;

    public OllirGeneratorVisitor(SymbolTable table) {
        this.table = table;
        exprVisitor = new OllirExprGeneratorVisitor(table);
    }


    @Override
    protected void buildVisitor() {

        addVisit(PROGRAM, this::visitProgram);
        addVisit(CLASS_DECL, this::visitClass);
        addVisit(METHOD_DECL, this::visitMethodDecl);
        addVisit(MAIN_METHOD_DECL, this::visitMainMethodDecl);
        addVisit(PARAM, this::visitParam);
        addVisit(RETURN_STMT, this::visitReturn);
        addVisit(ASSIGN_STMT, this::visitAssignStmt);
        addVisit(IMPORT_DECL, this::visitImports);
        addVisit(EXPR_STMT, this::visitExprStmt);
        addVisit(ARRAY_ASSIGN_STMT, this::visitArrayAssignStmt);
        addVisit(IF_STMT, this::visitIfStmt);
        addVisit(WHILE_STMT, this::visitWhileStmt);
        addVisit(BLOCK_STMT, this::visitBlockStmt);
        setDefaultVisit(this::defaultVisit);
    }

    private String visitBlockStmt(JmmNode node, Void unused){
        StringBuilder code = new StringBuilder();

        for (var child : node.getChildren()){
            code.append(visit(child));
        }

        return code.toString();
    }



    private String visitImports(JmmNode node, Void unused) {

        String imports = node.get("name");
        String output = "import ";
        String first = (imports.replace(", ", "."));
        String second = first.replace("[", "");
        second = second.replace("]", "");
        output += second + ";\n";

        return output;
    }




    private String visitAssignStmt(JmmNode node, Void unused) {

        var rhs = exprVisitor.visit(node.getJmmChild(0));
        StringBuilder code = new StringBuilder();


        Type thisType = TypeUtils.getExprType(node.getJmmChild(0), table);
        String typeString = "";
        if (thisType == null) {
            typeString = findType(node.get("name"), node.getJmmParent().get("name"));
        }
        else{
            typeString = OptUtils.toOllirType(thisType);
        }

        if (node.getJmmChild(0).getKind().equals(Kind.METHOD_CALL_EXPR.toString())){

            code.append(rhs.getComputation());
            code.append(node.get("name") + typeString + SPACE + ASSIGN + typeString + SPACE + rhs.getCode() + END_STMT);

            return code.toString();
        }


        //if(node.hasAttribute("name")) {
            var method = getMethod(node);
            if (isField(node.get("name")) && !isLocal(node.get("name"), method)) {

                if(!rhs.getComputation().isBlank() && rhs.getComputation().split(END_STMT).length == 1){
                    var b = rhs.getComputation().split(ASSIGN);
                    var comp = node.get("name") + typeString + SPACE + ASSIGN + typeString + SPACE + b[1];
                    code.append(comp);
                }
                else{
                    code.append(rhs.getComputation());
                }


                code.append("putfield(this, " + node.get("name") + typeString + ", " + rhs.getCode() + ").V" + END_STMT);
                return code.toString();
            }
        //}

        //StringBuilder code = new StringBuilder();

        var test = node.get("name");

        if(!rhs.getComputation().isBlank() && rhs.getComputation().split(END_STMT).length == 1){
            var b = rhs.getComputation().split(ASSIGN);
            System.out.println("B: " + b[1]);
            var comp = node.get("name") + typeString + SPACE + ASSIGN + b[1];
            System.out.println("COMP: " + comp);
            code.append(comp);
            return code.toString();
        }
        else{
            code.append(rhs.getComputation());
        }

        code.append(test);
        if (typeString.equals(".null") == false) {
            code.append(typeString);
        }
        code.append(SPACE);

        code.append(ASSIGN);
        if (typeString.equals(".null") == false) {
            code.append(typeString);
        }
        code.append(SPACE);

        code.append(rhs.getCode());

        code.append(END_STMT);

        return code.toString();
    }

    private String visitExprStmt(JmmNode node, Void unused) {

        var expr = exprVisitor.visit(node.getJmmChild(0));

        StringBuilder code = new StringBuilder();

        code.append(expr.getComputation());
        if (!node.getChild(0).getKind().equals(Kind.ASSIGN_STMT.toString())) {
            code.append(expr.getCode());
        }

        return code.toString();

    }

    private String visitReturn(JmmNode node, Void unused) {

        String methodName = node.getAncestor(METHOD_DECL).map(method -> method.get("name")).orElseThrow();
        Type retType = table.getReturnType(methodName);

        StringBuilder code = new StringBuilder();

        var expr = OllirExprResult.EMPTY;


        if (node.getNumChildren() > 0) {
            expr = exprVisitor.visit(node.getJmmChild(0));
        }

        code.append(expr.getComputation());
        code.append("ret");
        code.append(OptUtils.toOllirType(retType));
        code.append(SPACE);

        code.append(expr.getCode());

        code.append(END_STMT);

        return code.toString();
    }


    private String visitParam(JmmNode node, Void unused) {

        var typeCode = OptUtils.toOllirType(node.getJmmChild(0));
        var id = node.get("name");

        String code = id + typeCode;

        return code;
    }

    private String visitMainMethodDecl(JmmNode node, Void unused) {

        StringBuilder code = new StringBuilder(".method public static main(args.array.String).V");

        code.append(L_BRACKET);

        for (var child : node.getChildren()) {
            var childCode = visit(child);
            code.append(childCode);
        }
        code.append("ret.V ;\n");

        code.append(R_BRACKET);

        return code.toString();
    }

    private String visitMethodDecl(JmmNode node, Void unused) {

        StringBuilder code = new StringBuilder(".method ");

        boolean isPublic = NodeUtils.getBooleanAttribute(node, "isPublic", "false");

        if (isPublic) {
            code.append("public ");
        }

        var name = node.get("name");
        code.append(name);

        code.append("(");
        for (int i = 1; i<node.getChildren(Kind.PARAM).size() +1 ; i++) {
            var paramCode = visit(node.getJmmChild(i));
            if (i!=1)
                code.append(", ");
            code.append(paramCode);
        }
        code.append(")");

        var retType = OptUtils.toOllirType(node.getJmmChild(0));
        code.append(retType);
        code.append(L_BRACKET);

        var afterParam = node.getChildren(Kind.PARAM).size() +1;

        for (int i = afterParam; i < node.getNumChildren(); i++) {
            var child = node.getJmmChild(i);
            var childCode = visit(child);
            code.append(childCode);
        }

        code.append(R_BRACKET);
        code.append(NL);

        return code.toString();
    }


    private String visitClass(JmmNode node, Void unused) {

        StringBuilder code = new StringBuilder();

        code.append(table.getClassName());
        if (node.hasAttribute("parent")) {
            code.append(" extends ");
            code.append(node.get("parent"));
        }
        code.append(L_BRACKET);

        code.append(NL);
        var needNl = true;

        for (var child : node.getChildren(Kind.VAR_DECL)) {
            StringBuilder code2 = new StringBuilder();

            code2.append(".field public ");

            var type = child.getJmmChild(0);
            var id = child.get("name");

            code2.append(id);
            code2.append(OptUtils.toOllirType(type));
            code2.append(END_STMT);
            code.append(code2);
        }

        for (var child : node.getChildren()) {
            var result = visit(child);

            if (METHOD_DECL.check(child) && needNl) {
                code.append(NL);
                needNl = false;
            }

            code.append(result);
        }

        code.append(buildConstructor());
        code.append(R_BRACKET);


        return code.toString();
    }

    private String buildConstructor() {

        return ".construct " + table.getClassName() + "().V {\n" +
                "invokespecial(this, \"<init>\").V;\n" +
                "}\n";
    }


    private String visitProgram(JmmNode node, Void unused) {

        StringBuilder code = new StringBuilder();

        node.getChildren().stream()
                .map(this::visit)
                .forEach(code::append);


        System.out.println("FINAL CODE: " + code);
        return code.toString();
    }

    private String visitArrayAssignStmt(JmmNode node, Void unused) {

        StringBuilder code = new StringBuilder();
        String ollirType = OptUtils.toOllirType(new Type(TypeUtils.getIntArrayType().getName(), true));

        var name = node.get("name");
        if(isField(name) && !isLocal(name, getMethod(node))){
            var temp = OptUtils.getTemp();
            code.append(temp + ollirType + SPACE + ASSIGN + ollirType + SPACE + "getfield(this, " + name + ollirType + ")" + ollirType + END_STMT);
            name = temp;
        }

        var children1 = node.getJmmChild(0);
        String value = "";
        if(!children1.getKind().equals(Kind.INTEGER_LITERAL.toString())){
            var comp = exprVisitor.visit(children1);
            code.append(comp.getComputation());
            value = comp.getCode();
        }
        else{
            value = children1.get("value") + OptUtils.toOllirType(new Type(TypeUtils.getIntTypeName(), false));
        }
        var children2 = node.getJmmChild(1);

        String value2 = "";

        if (!children2.getKind().equals(Kind.INTEGER_LITERAL.toString())){
            var comp = exprVisitor.visit(children2);
            code.append(comp.getComputation());
            value2 = comp.getCode();
        }
        else{
            value2 = children2.get("value") + OptUtils.toOllirType(new Type(TypeUtils.getIntTypeName(), false));
        }
        var intType = new Type(TypeUtils.getIntTypeName(), false);
        String ollirIntType = OptUtils.toOllirType(intType);

        code.append(name + "[" + value + "]" + ollirIntType + SPACE + ASSIGN + ollirIntType + SPACE + value2 + END_STMT);

        return code.toString();
    }

    private String visitIfStmt(JmmNode node, Void unused) {

        StringBuilder code = new StringBuilder();

        boolean child1Block = false;
        boolean child2Block = false;

        var condition = exprVisitor.visit(node.getJmmChild(0));
        var thenStmt = node.getJmmChild(1);
        if(thenStmt.getKind().equals(Kind.BLOCK_STMT.toString())){
            child1Block = true;
        }
        var elseStmt = node.getJmmChild(2);
        if(elseStmt.getKind().equals(Kind.BLOCK_STMT.toString())){
            child2Block = true;
        }
        var nextIf = getNextIf();

        code.append(condition.getComputation());
        code.append("if(" + condition.getCode() + ") goto if_then_" + nextIf + ";\n");

        if(child2Block){
            for (var child : elseStmt.getChildren()){
                code.append(visit(child));
            }
        }
        else{
            code.append(visit(elseStmt));
        }


        code.append("goto if_end_" + nextIf + ";\n");

        code.append("if_then_" + nextIf + ":\n");

        if(child1Block){
            for (var child : thenStmt.getChildren()){
                code.append(visit(child));
            }
        }
        else{
            code.append(visit(thenStmt));
        }

        code.append("if_end_" + nextIf + ":\n");


        return code.toString();
    }

    private String visitWhileStmt(JmmNode node, Void unused) {

        StringBuilder code = new StringBuilder();

        var condition = exprVisitor.visit(node.getJmmChild(0));
        var stmt = node.getJmmChild(1);
        var nextWhile = getNextWhile();

        code.append("goto while_cond_" + nextWhile + ";\n");

        code.append("while_body_" + nextWhile + ":\n");

        for (var child : stmt.getChildren()){
            code.append(visit(child));
        }

        code.append("while_cond_" + nextWhile + ":\n");
        code.append(condition.getComputation());
        code.append("if (" + condition.getCode() + ") goto while_body_" + nextWhile + ";\n");

        return code.toString();
    }


    private String findType(String name, String method){

        for (var param: table.getParameters(method)){
            if (param.getName().equals(name)){
                return OptUtils.toOllirType(param.getType());
            }
        }

        for (var local : table.getLocalVariables(method)){
            if (local.getName().equals(name)){
                return OptUtils.toOllirType(local.getType());
            }
        }

        for (var field : table.getFields()){
            if (field.getName().equals(name)){
                return OptUtils.toOllirType(field.getType());
            }
        }

        return "";
    }

    private Boolean isField(String name){
        for (var field : table.getFields()){
            if (field.getName().equals(name)){
                return true;
            }
        }
        return false;
    }

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

    private int getNextIf(){
        return ++NEXT_IF;
    }

    private int getNextWhile(){
        return ++NEXT_WHILE;
    }

    /**
     * Default visitor. Visits every child node and return an empty string.
     *
     * @param node
     * @param unused
     * @return
     */
    private String defaultVisit(JmmNode node, Void unused) {

        for (var child : node.getChildren()) {
            visit(child);
        }

        return "";
    }
}
