package ua.yuriih.rustlexer;

import org.fusesource.jansi.AnsiConsole;

import java.io.BufferedInputStream;
import java.io.ByteArrayInputStream;
import java.io.FileInputStream;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;

import static org.fusesource.jansi.Ansi.ansi;

public class Main {
    public static void main(String[] args) throws IOException {
        //Might be necessary to get colored output in IntelliJ IDEA console
//        System.setProperty(AnsiConsole.JANSI_MODE, AnsiConsole.JANSI_MODE_FORCE);

        byte[] file = Files.readAllBytes(Path.of(args[0]));
        ByteArrayInputStream stream = new ByteArrayInputStream(file);
        Lexer lexer = new Lexer(stream);

        ArrayList<Token> tokens = lexer.parse();

        AnsiConsole.out().print(ansi().fgRed());
        for (Token token : tokens) {
            if (token.type == TokenType.ERROR)
                AnsiConsole.out().printf("%d:%d\t%s\t%s\n", token.line, token.column, token.type, token.value);
        }
        AnsiConsole.out().print(ansi().reset());

        stream.reset();
        Highlighter highlighter = new Highlighter();
        highlighter.printHighlighted(AnsiConsole.out(), stream, tokens);
    }
}
