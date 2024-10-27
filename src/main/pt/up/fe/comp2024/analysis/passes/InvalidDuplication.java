package pt.up.fe.comp2024.analysis.passes;

import pt.up.fe.comp.jmm.analysis.table.Symbol;
import pt.up.fe.comp.jmm.analysis.table.SymbolTable;
import pt.up.fe.comp.jmm.ast.JmmNode;
import pt.up.fe.comp.jmm.report.Report;
import pt.up.fe.comp.jmm.report.Stage;
import pt.up.fe.comp2024.analysis.AnalysisVisitor;
import pt.up.fe.comp2024.ast.Kind;
import pt.up.fe.comp2024.ast.NodeUtils;

import java.util.HashSet;
import java.util.List;
import java.util.Set;

public class InvalidDuplication extends AnalysisVisitor {

    @Override
    public void buildVisitor() {
        addVisit(Kind.CLASS_DECL, this::visitClassDecl);
    }

    //Check if imports, fields, methods, locals variables or parameters are duplicated
    private Void visitClassDecl(JmmNode classDecl, SymbolTable table) {
        List<String> imports = table.getImports();
        Set<String> importsSet = new HashSet<>(imports);
        Set<String> importsClass = new HashSet<>();

        imports.stream().forEach(importName -> {
            String[] importPath = importName.split("\\.");
            importsClass.add(importPath[importPath.length - 1]);
        });

        if (importsSet.size() != imports.size() || importsClass.size() != imports.size()) {
            var message = "Imports contains duplicates";
            addReport(Report.newError(
                    Stage.SEMANTIC,
                    NodeUtils.getLine(classDecl.getParent().getChild(0)),
                    NodeUtils.getColumn(classDecl.getParent().getChild(0)),
                    message,
                    null)
            );
        }

        List<Symbol> fields = table.getFields();
        Set<String> fieldsName = new HashSet<>();
        fields.stream().forEach(field -> fieldsName.add(field.getName()));

        if (fieldsName.size() != fields.size()) {
            var message = "Fields contains duplicates";
            addReport(Report.newError(
                    Stage.SEMANTIC,
                    NodeUtils.getLine(classDecl.getChild(0)),
                    NodeUtils.getColumn(classDecl.getChild(0)),
                    message,
                    null)
            );
        }

        List<String> methods = table.getMethods();
        Set<String> methodsSet = new HashSet<>(methods);

        if (methods.size() != methodsSet.size()) {
            var message = "Methods contains duplicates";
            addReport(Report.newError(
                    Stage.SEMANTIC,
                    NodeUtils.getLine(classDecl.getChild(0)),
                    NodeUtils.getColumn(classDecl.getChild(0)),
                    message,
                    null)
            );
        }

        table.getMethods().stream().forEach(method -> {
                    List<Symbol> localVars = table.getLocalVariables(method);
                    Set<String> localVarsSet = new HashSet<>();
                    localVars.stream().forEach(localVar -> localVarsSet.add(localVar.getName()));
                    if (localVars.size() != localVarsSet.size()) {
                        var message = String.format("Method %s contains duplicated local variables", method);
                        addReport(Report.newError(
                                Stage.SEMANTIC,
                                NodeUtils.getLine(classDecl),
                                NodeUtils.getColumn(classDecl),
                                message,
                                null)
                        );
                    }
                }
        );

        table.getMethods().stream().forEach(method -> {
                    List<Symbol> params = table.getParameters(method);
                    Set<String> paramsSet = new HashSet<>();
                    params.stream().forEach(param -> paramsSet.add(param.getName()));
                    if (params.size() != paramsSet.size()) {
                        var message = String.format("Method %s contains duplicated parameters", method);
                        addReport(Report.newError(
                                Stage.SEMANTIC,
                                NodeUtils.getLine(classDecl),
                                NodeUtils.getColumn(classDecl),
                                message,
                                null)
                        );
                    }
                }
        );

        return null;
    }
}
