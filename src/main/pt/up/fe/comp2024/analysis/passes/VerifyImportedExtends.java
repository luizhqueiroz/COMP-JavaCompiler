package pt.up.fe.comp2024.analysis.passes;

import pt.up.fe.comp.jmm.analysis.table.Symbol;
import pt.up.fe.comp.jmm.analysis.table.SymbolTable;
import pt.up.fe.comp.jmm.analysis.table.Type;
import pt.up.fe.comp.jmm.ast.JmmNode;
import pt.up.fe.comp.jmm.report.Report;
import pt.up.fe.comp.jmm.report.Stage;
import pt.up.fe.comp2024.analysis.AnalysisVisitor;
import pt.up.fe.comp2024.ast.Kind;
import pt.up.fe.comp2024.ast.NodeUtils;
import pt.up.fe.comp2024.ast.TypeUtils;

public class VerifyImportedExtends extends AnalysisVisitor {
    @Override
    public void buildVisitor() {
        addVisit(Kind.CLASS_DECL, this::visitClassDecl);
    }

    private Boolean isFromImportedClass(String className, SymbolTable table) {
        for (String importName : table.getImports()) {
            String[] imports = importName.split("\\.");
            if (imports[imports.length - 1].equals(className)) {
                return true;
            }
        }

        return false;
    }

    private Void visitClassDecl(JmmNode node, SymbolTable table) {
        String className = node.get("name");
        String superClass = node.getOptional("parent").orElse(null);
        if (superClass != null) {
            if (!isFromImportedClass(superClass, table)) {
                var message = "Class '" + className + "' extends class '" + superClass + "' which is not imported";
                addReport(Report.newError(
                        Stage.SEMANTIC,
                        NodeUtils.getLine(node),
                        NodeUtils.getColumn(node),
                        message,
                        null)
                );

            }
        }
        return null;
    }
}
