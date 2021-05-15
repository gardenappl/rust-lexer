package ua.yuriih.rustlexer;

enum State {
    INITIAL,
    ID_OR_KEYWORD,
    ID_OR_UNDERSCORE,
    POSSIBLY_RAW_STRING,
    POSSIBLY_BYTE_OR_BYTE_STRING,
    CHAR_LITERAL_OR_LIFETIME_OR_LABEL,
    LIFETIME_OR_LABEL,
    STRING_LITERAL,
    CHAR_LITERAL,
    BYTE_LITERAL,
    BYTE_STRING_LITERAL,
    RAW_STRING_LITERAL,
    NUMBER_LITERAL,
    SLASH,
    PUNCTUATION;

    enum StringEscape {
        NONE,
        SLASH,
        ASCII_OR_BYTE,
        UNICODE
    }
}