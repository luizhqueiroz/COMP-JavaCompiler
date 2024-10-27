package pt.up.fe.comp2024.symboltable;

import pt.up.fe.comp.jmm.analysis.table.Symbol;
import pt.up.fe.comp.jmm.analysis.table.Type;
import pt.up.fe.comp.jmm.ast.JmmNode;

import java.util.*;
import java.util.stream.Collectors;

import static pt.up.fe.comp2024.ast.Kind.*;

public class JmmSymbolTableBuilder {


    public static JmmSymbolTable build(JmmNode root) {

        //SpecsCheck.checkArgument(Kind.CLASS_DECL.check(classDecl), () -> "Expected a class declaration: " + classDecl);
        var classDecl = root.getChildren(CLASS_DECL).get(0);
        var importDecl = root.getChildren(IMPORT_DECL);

        String className = classDecl.get("name");
        String superClassName;
        if (classDecl.hasAttribute("parent")) {
            superClassName = classDecl.get("parent");
        } else {
            superClassName = "";
        }

        var imports = buildImports(importDecl);
        var fields = buildFields(classDecl);
        var methods = buildMethods(classDecl);
        var returnTypes = buildReturnTypes(classDecl);
        var params = buildParams(classDecl);
        var locals = buildLocals(classDecl);

        return new JmmSymbolTable(imports, className, superClassName, fields, methods, returnTypes, params, locals);
    }

    private static List<String> buildImports(List<JmmNode> importDecl) {
        if (importDecl.isEmpty()) return Collections.emptyList();

        return importDecl.stream().map(imp -> String.join(".", imp.getObjectAsList("name", String.class))).toList();
    }

    private static Map<String, Type> buildReturnTypes(JmmNode classDecl) {
        // TODO: Simple implementation that needs to be expanded

        Map<String, Type> map = new HashMap<>();

        classDecl.getChildren(MAIN_METHOD_DECL).stream()
                .forEach(method -> map.put("main", new Type("void", false)));

        classDecl.getChildren(METHOD_DECL).stream()
                .forEach(method -> {
                    if (method.getChild(0).hasAttribute("varArg")) {
                        Type type = new Type(method.getChild(0).get("name"), true);
                        type.putObject("varArg", true);
                        map.put(method.get("name"), type);
                    }
                    else {
                        map.put(method.get("name"),
                            new Type(method.getChild(0).get("name"), method.getChild(0).hasAttribute("array")));
                    }
                });

        //new Type(TypeUtils.getIntTypeName(), false)));

        return map;
    }

    private static Map<String, List<Symbol>> buildParams(JmmNode classDecl) {
        // TODO: Simple implementation that needs to be expanded

        Map<String, List<Symbol>> map = new HashMap<>();

        //var intType = new Type(TypeUtils.getIntTypeName(), false);

        classDecl.getChildren(MAIN_METHOD_DECL).stream()
                .forEach(method -> map.put("main", Arrays.asList(new Symbol(new Type("String", true), method.get("var")))));

        classDecl.getChildren(METHOD_DECL).stream()
                .forEach(method -> map.put(method.get("name"),
                        method.getChildren(PARAM).stream().map(param -> {
                            if (param.getChild(0).hasAttribute("varArg")) {
                                Type type = new Type(param.getChild(0).get("name"), true);
                                type.putObject("varArg", true);
                                return new Symbol(type, param.get("name"));
                            } else {
                                return new Symbol(
                                        new Type(param.getChild(0).get("name"), param.getChild(0).hasAttribute("array")), param.get("name"));
                            }
                        }).toList()));

        //classDecl.getChildren(METHOD_DECL).stream()
        //                .forEach(method -> map.put(method.get("name"), Arrays.asList(new Symbol(intType, method.getJmmChild(1).get("name")))));

        return map;
    }

    private static Map<String, List<Symbol>> buildLocals(JmmNode classDecl) {
        // TODO: Simple implementation that needs to be expanded

        Map<String, List<Symbol>> map = new HashMap<>();

        classDecl.getChildren(MAIN_METHOD_DECL).stream()
                .forEach(method -> map.put(method.get("name"), getLocalsList(method)));

        classDecl.getChildren(METHOD_DECL).stream()
                .forEach(method -> map.put(method.get("name"), getLocalsList(method)));

        return map;
    }

    private static List<String> buildMethods(JmmNode classDecl) {

        List<String> methods = classDecl.getChildren(METHOD_DECL).stream()
                .map(method -> method.get("name"))
                .collect(Collectors.toList());

        if (!classDecl.getChildren(MAIN_METHOD_DECL).isEmpty()) {
            methods.add("main");
        }

        return methods;
    }

    private static List<Symbol> buildFields(JmmNode classDecl) {

        return classDecl.getChildren(VAR_DECL).stream().map(field -> new Symbol(new Type(field.getChild(0).get("name"), field.getChild(0).hasAttribute("array")), field.get("name"))).toList();
    }


    private static List<Symbol> getLocalsList(JmmNode methodDecl) {
        // TODO: Simple implementation that needs to be expanded

        //var intType = new Type(TypeUtils.getIntTypeName(), false);

        return methodDecl.getChildren(VAR_DECL).stream()
                .map(varDecl -> new Symbol(new Type(varDecl.getChild(0).get("name"), varDecl.getChild(0).hasAttribute("array")), varDecl.get("name")))
                .toList();
    }
}
