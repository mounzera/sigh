import norswap.autumn.AutumnTestFixture;
import norswap.sigh.SighGrammar;
import norswap.sigh.ast.*;
import norswap.sigh.types.ArrayType;
import norswap.sigh.types.TemplateType;
import org.testng.annotations.Test;

import static java.util.Arrays.asList;
import norswap.autumn.AutumnTestFixture;
    import norswap.sigh.SighGrammar;
    import norswap.sigh.ast.*;
    import org.testng.annotations.Test;

    import java.util.ArrayList;
    import java.util.List;

    import static java.util.Arrays.asList;

public class ArrayGrammarTests extends AutumnTestFixture {
    private final SighGrammar grammar = new SighGrammar();
    private final Class<?> grammarClass = grammar.getClass();

    private static IntLiteralNode intlit (long i) {
        return new IntLiteralNode(null, i);
    }

    private static FloatLiteralNode floatlit (double d) {
        return new FloatLiteralNode(null, d);
    }

    private static TemplateTypeNode templit (String t) { return  new TemplateTypeNode(null,t);}

    @Test
    public void testDeclarations() {
        rule = grammar.statement;
        System.out.println(new ArrayTypeNode(null,new TemplateTypeNode(null, "Template")).componentType);
        System.out.println(new ArrayLiteralNode(null,asList()));
        successExpect("var a:Template[]=[]", new VarDeclarationNode(null,"a",new ArrayTypeNode(null,new TemplateTypeNode(null, "Template")),new ArrayLiteralNode(null,asList())));








        //failure(" template <typename T> fun max (a: T, b:T); max<double, int>(2.6, 1);");
        //failure(" template <typename T> fun max (a: T, b:T2); max<double>(2.6, 1);");

    }
}

