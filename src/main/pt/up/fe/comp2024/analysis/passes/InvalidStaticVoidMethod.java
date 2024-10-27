package pt.up.fe.comp2024.analysis.passes;


import pt.up.fe.comp.jmm.analysis.table.SymbolTable;
import pt.up.fe.comp.jmm.ast.JmmNode;
import pt.up.fe.comp.jmm.report.Report;
import pt.up.fe.comp.jmm.report.Stage;
import pt.up.fe.comp2024.analysis.AnalysisVisitor;
import pt.up.fe.comp2024.ast.Kind;
import pt.up.fe.comp2024.ast.NodeUtils;
import pt.up.fe.specs.util.SpecsCheck;

import java.util.Objects;

public class InvalidStaticVoidMethod extends AnalysisVisitor{


    @Override
    protected void buildVisitor() {
        addVisit(Kind.MAIN_METHOD_DECL, this::visitMainMethod);
    }

    private Void visitMainMethod(JmmNode method, SymbolTable table){

        String name = method.get("name");

        if (Objects.equals(name, "main")){
            return null;
        }

        var message = String.format("Invalid name for static void method. Expected 'main' but got '%s'.", name);

        addReport(Report.newError(
                Stage.SEMANTIC,
                NodeUtils.getLine(method),
                NodeUtils.getColumn(method),
                message,
                null)
        );


        return null;
    }


}
