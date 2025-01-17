import norswap.autumn.AutumnTestFixture;
import norswap.sigh.SighGrammar;
import norswap.sigh.ast.*;
import org.testng.annotations.Test;

import java.util.ArrayList;
import java.util.List;

import static java.util.Arrays.asList;

public class TemplateGrammarTests extends AutumnTestFixture {
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
        successExpect("template <typename T> fun f (x: T) {}",
            new FunDeclarationNode(null,
                asList(new TemplateParameterNode(null, "T", new TemplateTypeNode(null, "T"))),
                "f",
                asList(new ParameterNode(null, "x", new SimpleTypeNode(null, "T"))),
                new SimpleTypeNode(null, "Void"),
                new BlockNode(null, asList())));
        successExpect("template <typename T> fun f (x: T):T { return 2}",
            new FunDeclarationNode(null,
                asList(new TemplateParameterNode(null, "T", new TemplateTypeNode(null, "T"))),
                "f",
                asList(new ParameterNode(null, "x", new SimpleTypeNode(null, "T"))),
                new SimpleTypeNode(null, "T"),
                new BlockNode(null, asList(new ReturnNode(null, intlit(2) )))));

        successExpect("template <typename T> fun f (x: T):T { return x}",
            new FunDeclarationNode(null,
                asList(new TemplateParameterNode(null, "T", new TemplateTypeNode(null, "T"))),
                "f",
                asList(new ParameterNode(null, "x", new SimpleTypeNode(null, "T"))),
                new SimpleTypeNode(null, "T"),
                new BlockNode(null, asList(new ReturnNode(null, new ReferenceNode(null,"x") )))));



        successExpect("template <typename T,typename T1> fun f (x: T):T { return x}",
            new FunDeclarationNode(null,
                asList(new TemplateParameterNode(null, "T", new TemplateTypeNode(null, "T")),
                    new TemplateParameterNode(null, "T1", new TemplateTypeNode(null,"T1"))),
                "f",
                asList(new ParameterNode(null, "x", new SimpleTypeNode(null, "T"))),
                new SimpleTypeNode(null, "T"),
                new BlockNode(null, asList(new ReturnNode(null, new ReferenceNode(null,"x") )))));


        successExpect("template <typename T,typename T1> fun f (x: T1, y:T, z:Int, a: T1):T { return x}",
            new FunDeclarationNode(null,
                asList(new TemplateParameterNode(null, "T", new TemplateTypeNode(null, "T")),
                    new TemplateParameterNode(null, "T1", new TemplateTypeNode(null,"T1"))),
                "f",
                asList(new ParameterNode(null, "x", new SimpleTypeNode(null, "T1")),
                        new ParameterNode(null,"y",new SimpleTypeNode(null, "T")),
                        new ParameterNode(null, "z", new SimpleTypeNode(null, "Int")),
                        new ParameterNode(null, "a", new SimpleTypeNode(null, "T1"))),
                new SimpleTypeNode(null, "T"),
                new BlockNode(null, asList(new ReturnNode(null, new ReferenceNode(null,"x") )))));

        successExpect("template <typename T,typename T1> fun f (x: T1, y:T, z:Int, a: T1):Int { return x}",
            new FunDeclarationNode(null,
                asList(new TemplateParameterNode(null, "T", new TemplateTypeNode(null, "T")),
                    new TemplateParameterNode(null, "T1", new TemplateTypeNode(null,"T1"))),
                "f",
                asList(new ParameterNode(null, "x", new SimpleTypeNode(null, "T1")),
                    new ParameterNode(null,"y",new SimpleTypeNode(null, "T")),
                    new ParameterNode(null, "z", new SimpleTypeNode(null, "Int")),
                    new ParameterNode(null, "a", new SimpleTypeNode(null, "T1"))),
                new SimpleTypeNode(null, "Int"),
                new BlockNode(null, asList(new ReturnNode(null, new ReferenceNode(null,"x") )))));

        failure("template <typename T> fun max (a: T, b:T); max<double, int>(2.6, 1);");
        failure("template <typename T> fun max (a: T, b:T2); max<double>(2.6, 1);");


        // Struct with template tests
        successExpect("template<typename T> struct Pair {}", new StructDeclarationNode(null, asList(new TemplateParameterNode(null, "T", new TemplateTypeNode(null, "T"))),"Pair", asList()));
        successExpect("template<typename T> struct Pair {var a:T}", new StructDeclarationNode(null, asList(new TemplateParameterNode(null, "T", new TemplateTypeNode(null, "T"))),"Pair", asList(new FieldDeclarationNode(null, "a", new SimpleTypeNode(null, "T")))));
        successExpect("template<typename T, typename T1> struct P { var x: Int; var y: T1; var z: T }",
            new StructDeclarationNode(null, asList(new TemplateParameterNode(null, "T", new TemplateTypeNode(null, "T")), new TemplateParameterNode(null, "T1", new TemplateTypeNode(null, "T1"))),"P", asList(
                new FieldDeclarationNode(null, "x", new SimpleTypeNode(null, "Int")),
                new FieldDeclarationNode(null, "y", new SimpleTypeNode(null, "T1")), new FieldDeclarationNode(null, "z", new SimpleTypeNode(null, "T")))));

        failure("template <typename T> struct P {a: T1};");
        failure("struct P {a: T1};");


    }
}
