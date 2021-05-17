package ua.yuriih.rustlexer;

import org.fusesource.jansi.Ansi;
import org.fusesource.jansi.AnsiPrintStream;

import java.io.IOException;
import java.io.InputStream;
import java.util.ArrayList;
import java.util.List;
import java.util.stream.Stream;

import static org.fusesource.jansi.Ansi.ansi;

public class Highlighter {
    public void printHighlighted(AnsiPrintStream printStream, InputStream source, ArrayList<Token> tokens) throws IOException {
        int row = 0;
        int column = 0;

        int tokenNum = 0;

        char c = 0;
        while (true) {
            int read = source.read();
            if (read < 0)
                return;
            c = (char) read;

            boolean highlightError = false;
            while (tokenNum < tokens.size()) {
                Token token = tokens.get(tokenNum);

                if (token != null && token.line == row && token.column == column) {
                    printStream.print(ansi().reset());
                    switch (token.type) {
                        case AS,
                                BREAK,
                                CONST,
                                CONTINUE,
                                CRATE,
                                ELSE,
                                ENUM,
                                EXTERN,
                                FALSE,
                                FN,
                                FOR,
                                IF,
                                IMPL,
                                IN,
                                LET,
                                LOOP,
                                MATCH,
                                MOD,
                                MOVE,
                                MUT,
                                PUB,
                                REF,
                                RETURN,
                                SELF_VALUE,
                                SELF_TYPE,
                                STATIC,
                                STRUCT,
                                SUPER,
                                TRAIT,
                                TRUE,
                                TYPE,
                                UNSAFE,
                                USE,
                                WHERE,
                                WHILE,
                                ASYNC,
                                AWAIT,
                                DYN,
                                ABSTRACT,
                                BECOME,
                                BOX,
                                DO,
                                FINAL,
                                MACRO,
                                OVERRIDE,
                                PRIV,
                                TYPEOF,
                                UNSIZED,
                                VIRTUAL,
                                YIELD,
                                TRY,
                                UNION,
                                STATIC_LIFETIME -> printStream.print(ansi().fgMagenta());

                        case IDENTIFIER,
                                RAW_IDENTIFIER -> printStream.print(ansi().fgCyan());

                        case COMMENT,
                                COMMENT_INNER_DOC,
                                COMMENT_OUTER_DOC -> printStream.print(ansi().a(Ansi.Attribute.ITALIC).fgBrightBlack());

                        case LABEL,
                                LIFETIME -> printStream.print(ansi().fgYellow());

                        case CHAR_LITERAL,
                                BYTE_LITERAL -> printStream.print(ansi().a(Ansi.Attribute.ITALIC).fgBlue());
                        case STRING_LITERAL,
                                RAW_STRING_LITERAL,
                                BYTE_STRING_LITERAL,
                                RAW_BYTE_STRING_LITERAL -> printStream.print(ansi().fgGreen());
                        case INT_LITERAL_DEC,
                                INT_LITERAL_HEX,
                                INT_LITERAL_OCTAL,
                                INT_LITERAL_BIN,
                                FLOAT_LITERAL -> printStream.print(ansi().fgBlue());

                        case PLUS,
                                MINUS,
                                STAR,
                                SLASH,
                                PERCENT,
                                CARET,
                                NOT,
                                AND,
                                OR,
                                AND_AND,
                                OR_OR,
                                SHL,
                                SHR,
                                PLUS_EQ,
                                MINUS_EQ,
                                STAR_EQ,
                                SLASH_EQ,
                                PERCENT_EQ,
                                CARET_EQ,
                                AND_EQ,
                                OR_EQ,
                                SHL_EQ,
                                SHR_EQ,
                                EQ,
                                EQ_EQ,
                                NE,
                                GT,
                                LT,
                                GE,
                                LE,
                                AT,
                                UNDERSCORE,
                                DOT,
                                DOT_DOT,
                                DOT_DOT_DOT,
                                DOT_DOT_EQ,
                                COMMA,
                                SEMICOLON,
                                COLON,
                                PATH_SEPARATOR,
                                R_ARROW,
                                FAT_ARROW,
                                POUND,
                                DOLLAR,
                                QUESTION,

                                CURLY_L,
                                CURLY_R,
                                SQUARE_L,
                                SQUARE_R,
                                PAREN_L,
                                PAREN_R -> { }

                        case ERROR -> highlightError = true;
                    }
                    tokenNum++;
                } else {
                    break;
                }
            }

            if (highlightError)
                printStream.print(ansi().bgRed());

            printStream.print(c);

            if (c == '\n') {
                row++;
                column = 0;
            } else {
                column++;
            }
        }
    }
}
