import norswap.autumn.AutumnTestFixture;
import norswap.sigh.SighGrammar;
import norswap.sigh.ast.*;
import org.testng.annotations.Test;

import static java.util.Arrays.asList;

public class TemplateGrammarTests extends AutumnTestFixture {
    private final SighGrammar grammar = new SighGrammar();
    private final Class<?> grammarClass = grammar.getClass();


    @Test
    public void testDeclarations() {
        rule = grammar.statement;

        successExpect("template <typename T> fun f (x: T): T {}",
            new FunDeclarationNode(null, asList(new TemplateParameterNode(null, "T", new TemplateTypeNode(null))), "f",
                asList(new ParameterNode(null, "x", new SimpleTypeNode(null, "T"))),
                new SimpleTypeNode(null, "T"),
                new BlockNode(null, null)));
    }
}
