package pt.up.fe.comp2024.analysis;

import pt.up.fe.comp.jmm.analysis.JmmAnalysis;
import pt.up.fe.comp.jmm.analysis.JmmSemanticsResult;
import pt.up.fe.comp.jmm.analysis.table.SymbolTable;
import pt.up.fe.comp.jmm.ast.JmmNode;
import pt.up.fe.comp.jmm.parser.JmmParserResult;
import pt.up.fe.comp.jmm.report.Report;
import pt.up.fe.comp.jmm.report.Stage;
import pt.up.fe.comp2024.analysis.passes.*;
import pt.up.fe.comp2024.symboltable.JmmSymbolTableBuilder;

import java.util.ArrayList;
import java.util.List;

public class JmmAnalysisImpl implements JmmAnalysis {


    private final List<AnalysisPass> analysisPasses;

    public JmmAnalysisImpl() {

        //this.analysisPasses = List.of(new UndeclaredVariable(), new UndeclaredVariable(), new InvalidStaticVoidMethod());
        this.analysisPasses = new ArrayList<>();
        analysisPasses.add(new UndeclaredVariable());
        analysisPasses.add(new InvalidStaticVoidMethod());
        analysisPasses.add(new IncompatibleOperation());
        analysisPasses.add(new InvalidArray());
        analysisPasses.add(new IncompatibleAssignment());
        analysisPasses.add(new InvalidCondition());
        analysisPasses.add(new InvalidVarargs());
        analysisPasses.add(new VerifyImportedExtends());
        analysisPasses.add(new InvalidDuplication());
        analysisPasses.add(new ModifyVarargs());
    }

    @Override
    public JmmSemanticsResult semanticAnalysis(JmmParserResult parserResult) {

        JmmNode rootNode = parserResult.getRootNode();

        SymbolTable table = JmmSymbolTableBuilder.build(rootNode);

        List<Report> reports = new ArrayList<>();

        // Visit all nodes in the AST
        for (var analysisPass : analysisPasses) {
            try {
                var passReports = analysisPass.analyze(rootNode, table);
                reports.addAll(passReports);
            } catch (Exception e) {
                reports.add(Report.newError(Stage.SEMANTIC,
                        -1,
                        -1,
                        "Problem while executing analysis pass '" + analysisPass.getClass() + "'",
                        e)
                );
            }

        }

        // print all reports
        for (var report : reports) {
            System.out.println(report);
        }

        return new JmmSemanticsResult(parserResult, table, reports);
    }
}
