use once_cell::sync::Lazy;
use ratatui::style::Style;
use ratatui::text::{Line, Span};
use std::collections::HashSet;
use std::path::Path;

#[derive(Debug, Clone, Copy, PartialEq, Eq)]
pub enum Language {
    Java,
    Rust,
    Python,
    C,
    Cpp,
    JavaScript,
    TypeScript,
    Json,
    Html,
    Css,
    Markdown,
    Toml,
    Yaml,
    Shell,
    Generic,
}

impl Language {
    pub fn from_path(path: Option<&Path>) -> Self {
        let ext = path
            .and_then(|p| p.extension())
            .and_then(|e| e.to_str())
            .unwrap_or("")
            .to_lowercase();

        match ext.as_str() {
            "java" => Language::Java,
            "rs" => Language::Rust,
            "py" | "pyw" => Language::Python,
            "c" | "h" => Language::C,
            "cpp" | "cxx" | "cc" | "hpp" => Language::Cpp,
            "js" | "jsx" | "mjs" => Language::JavaScript,
            "ts" | "tsx" => Language::TypeScript,
            "json" => Language::Json,
            "html" | "htm" => Language::Html,
            "css" | "scss" | "sass" => Language::Css,
            "md" | "markdown" => Language::Markdown,
            "toml" => Language::Toml,
            "yaml" | "yml" => Language::Yaml,
            "sh" | "bash" | "zsh" | "ps1" => Language::Shell,
            _ => Language::Generic,
        }
    }

    pub fn display_name(&self) -> &'static str {
        match self {
            Language::Java => "Java",
            Language::Rust => "Rust",
            Language::Python => "Python",
            Language::C => "C",
            Language::Cpp => "C++",
            Language::JavaScript => "JavaScript",
            Language::TypeScript => "TypeScript",
            Language::Json => "JSON",
            Language::Html => "HTML",
            Language::Css => "CSS",
            Language::Markdown => "Markdown",
            Language::Toml => "TOML",
            Language::Yaml => "YAML",
            Language::Shell => "Shell",
            Language::Generic => "Plain Text",
        }
    }

    fn keywords(&self) -> &'static HashSet<&'static str> {
        match self {
            Language::Java => &JAVA_KEYWORDS,
            Language::Rust => &RUST_KEYWORDS,
            Language::Python => &PYTHON_KEYWORDS,
            Language::C => &C_KEYWORDS,
            Language::Cpp => &CPP_KEYWORDS,
            Language::JavaScript | Language::TypeScript => &JS_KEYWORDS,
            Language::Json => &JSON_KEYWORDS,
            Language::Shell => &SHELL_KEYWORDS,
            _ => &EMPTY_KEYWORDS,
        }
    }

    fn hash_comment(&self) -> bool {
        matches!(
            self,
            Language::Python | Language::Shell | Language::Yaml | Language::Toml
        )
    }
}

static EMPTY_KEYWORDS: Lazy<HashSet<&'static str>> = Lazy::new(HashSet::new);

static JAVA_KEYWORDS: Lazy<HashSet<&'static str>> = Lazy::new(|| {
    [
        "abstract",
        "assert",
        "boolean",
        "break",
        "byte",
        "case",
        "catch",
        "char",
        "class",
        "const",
        "continue",
        "default",
        "do",
        "double",
        "else",
        "enum",
        "extends",
        "final",
        "finally",
        "float",
        "for",
        "goto",
        "if",
        "implements",
        "import",
        "instanceof",
        "int",
        "interface",
        "long",
        "native",
        "new",
        "package",
        "private",
        "protected",
        "public",
        "return",
        "short",
        "static",
        "strictfp",
        "super",
        "switch",
        "synchronized",
        "this",
        "throw",
        "throws",
        "transient",
        "try",
        "void",
        "volatile",
        "while",
        "record",
        "sealed",
        "permits",
        "var",
        "yield",
        "module",
        "requires",
        "exports",
        "opens",
        "to",
        "uses",
        "with",
        "by",
        "transitive",
        "provides",
        "true",
        "false",
        "null",
    ]
    .into_iter()
    .collect()
});

static RUST_KEYWORDS: Lazy<HashSet<&'static str>> = Lazy::new(|| {
    [
        "as", "async", "await", "break", "const", "continue", "crate", "dyn", "else", "enum",
        "extern", "false", "fn", "for", "if", "impl", "in", "let", "loop", "match", "mod", "move",
        "mut", "pub", "ref", "return", "self", "Self", "static", "struct", "super", "trait",
        "true", "type", "unsafe", "use", "where", "while", "abstract", "become", "box", "do",
        "final", "macro", "override", "priv", "typeof", "unsized", "virtual", "yield", "try",
        "union",
    ]
    .into_iter()
    .collect()
});

static PYTHON_KEYWORDS: Lazy<HashSet<&'static str>> = Lazy::new(|| {
    [
        "and", "as", "assert", "async", "await", "break", "class", "continue", "def", "del",
        "elif", "else", "except", "False", "finally", "for", "from", "global", "if", "import",
        "in", "is", "lambda", "None", "nonlocal", "not", "or", "pass", "raise", "return", "True",
        "try", "while", "with", "yield", "match", "case",
    ]
    .into_iter()
    .collect()
});

static C_KEYWORDS: Lazy<HashSet<&'static str>> = Lazy::new(|| {
    [
        "auto", "break", "case", "char", "const", "continue", "default", "do", "double", "else",
        "enum", "extern", "float", "for", "goto", "if", "inline", "int", "long", "register",
        "restrict", "return", "short", "signed", "sizeof", "static", "struct", "switch", "typedef",
        "union", "unsigned", "void", "volatile", "while", "_Bool",
    ]
    .into_iter()
    .collect()
});

static CPP_KEYWORDS: Lazy<HashSet<&'static str>> = Lazy::new(|| {
    [
        "alignas",
        "alignof",
        "and",
        "and_eq",
        "asm",
        "auto",
        "bitand",
        "bitor",
        "bool",
        "break",
        "case",
        "catch",
        "char",
        "char8_t",
        "class",
        "compl",
        "concept",
        "const",
        "consteval",
        "constexpr",
        "constinit",
        "const_cast",
        "continue",
        "co_await",
        "co_return",
        "co_yield",
        "decltype",
        "default",
        "delete",
        "do",
        "double",
        "dynamic_cast",
        "else",
        "enum",
        "explicit",
        "export",
        "extern",
        "false",
        "float",
        "for",
        "friend",
        "goto",
        "if",
        "inline",
        "int",
        "long",
        "mutable",
        "namespace",
        "new",
        "noexcept",
        "not",
        "not_eq",
        "nullptr",
        "operator",
        "or",
        "or_eq",
        "private",
        "protected",
        "public",
        "register",
        "reinterpret_cast",
        "requires",
        "return",
        "short",
        "signed",
        "sizeof",
        "static",
        "static_assert",
        "static_cast",
        "struct",
        "switch",
        "template",
        "this",
        "thread_local",
        "throw",
        "true",
        "try",
        "typedef",
        "typeid",
        "typename",
        "union",
        "unsigned",
        "using",
        "virtual",
        "void",
        "volatile",
        "wchar_t",
        "while",
        "xor",
        "xor_eq",
    ]
    .into_iter()
    .collect()
});

static JS_KEYWORDS: Lazy<HashSet<&'static str>> = Lazy::new(|| {
    [
        "async",
        "await",
        "break",
        "case",
        "catch",
        "class",
        "const",
        "continue",
        "debugger",
        "default",
        "delete",
        "do",
        "else",
        "export",
        "extends",
        "false",
        "finally",
        "for",
        "function",
        "if",
        "import",
        "in",
        "instanceof",
        "let",
        "new",
        "null",
        "return",
        "static",
        "super",
        "switch",
        "this",
        "throw",
        "true",
        "try",
        "typeof",
        "undefined",
        "var",
        "void",
        "while",
        "with",
        "yield",
        "interface",
        "type",
        "enum",
        "implements",
    ]
    .into_iter()
    .collect()
});

static JSON_KEYWORDS: Lazy<HashSet<&'static str>> =
    Lazy::new(|| ["true", "false", "null"].into_iter().collect());

static SHELL_KEYWORDS: Lazy<HashSet<&'static str>> = Lazy::new(|| {
    [
        "if", "then", "else", "elif", "fi", "for", "while", "do", "done", "case", "esac",
        "function", "return", "export", "local", "source", "echo", "cd", "set",
    ]
    .into_iter()
    .collect()
});

static PRIMITIVE_TYPES: Lazy<HashSet<&'static str>> = Lazy::new(|| {
    [
        "byte", "short", "int", "long", "float", "double", "char", "boolean", "void", "String",
        "Object", "u8", "u16", "u32", "u64", "i8", "i16", "i32", "i64", "usize", "isize", "f32",
        "f64", "bool", "str", "None", "Some", "Result", "Option", "Vec",
    ]
    .into_iter()
    .collect()
});

#[derive(Debug, Clone, Copy, PartialEq, Eq)]
pub enum TokenKind {
    Keyword,
    PrimitiveType,
    Identifier,
    Function,
    StringLiteral,
    CharLiteral,
    Comment,
    Number,
    Operator,
    Separator,
    Annotation,
    Whitespace,
    Unknown,
}

#[derive(Debug, Clone)]
pub struct HighlightedToken {
    pub kind: TokenKind,
    pub text: String,
    pub start: usize,
    pub end: usize,
}

pub fn highlight_line(line: &str, language: Language) -> Vec<HighlightedToken> {
    let mut state = false;
    highlight_line_with_state(line, language, &mut state)
}

pub fn highlight_lines(lines: &[String], language: Language) -> Vec<Vec<HighlightedToken>> {
    let mut state = false;
    lines
        .iter()
        .map(|line| highlight_line_with_state(line, language, &mut state))
        .collect()
}

pub fn highlight_line_with_state(
    line: &str,
    language: Language,
    in_block_comment: &mut bool,
) -> Vec<HighlightedToken> {
    let chars: Vec<char> = line.chars().collect();
    let mut tokens = Vec::new();
    let mut i = 0;
    let mut col = 0;

    let push = |tokens: &mut Vec<HighlightedToken>,
                kind: TokenKind,
                text: String,
                start: usize,
                end: usize| {
        tokens.push(HighlightedToken {
            kind,
            text,
            start,
            end,
        });
    };

    if *in_block_comment {
        while i < chars.len() {
            if chars[i] == '*' && i + 1 < chars.len() && chars[i + 1] == '/' {
                let text: String = chars[i..i + 2].iter().collect();
                push(&mut tokens, TokenKind::Comment, text, col, col + 2);
                i += 2;
                col += 2;
                *in_block_comment = false;
                break;
            }
            i += 1;
            col += 1;
        }
        if *in_block_comment {
            let text: String = chars[i..].iter().collect();
            if !text.is_empty() {
                push(
                    &mut tokens,
                    TokenKind::Comment,
                    text,
                    col,
                    col + chars.len() - i,
                );
            }
            return tokens;
        }
    }

    while i < chars.len() {
        let c = chars[i];

        if c.is_whitespace() {
            let mut ws = String::new();
            while i < chars.len() && chars[i].is_whitespace() {
                ws.push(chars[i]);
                i += 1;
            }
            push(
                &mut tokens,
                TokenKind::Whitespace,
                ws.clone(),
                col,
                col + ws.chars().count(),
            );
            col += ws.chars().count();
            continue;
        }

        if c == '/' && i + 1 < chars.len() && chars[i + 1] == '/' {
            let text: String = chars[i..].iter().collect();
            push(
                &mut tokens,
                TokenKind::Comment,
                text.clone(),
                col,
                col + text.chars().count(),
            );
            col += text.chars().count();
            i = chars.len();
            continue;
        }

        if c == '/' && i + 1 < chars.len() && chars[i + 1] == '*' {
            let start_col = col;
            let mut text = String::from("/*");
            i += 2;
            col += 2;
            while i + 1 < chars.len() && !(chars[i] == '*' && chars[i + 1] == '/') {
                text.push(chars[i]);
                i += 1;
                col += 1;
            }
            if i + 1 < chars.len() {
                text.push('*');
                text.push('/');
                i += 2;
                col += 2;
            } else {
                *in_block_comment = true;
                while i < chars.len() {
                    text.push(chars[i]);
                    i += 1;
                    col += 1;
                }
            }
            push(&mut tokens, TokenKind::Comment, text, start_col, col);
            continue;
        }

        if language.hash_comment() && c == '#' {
            let text: String = chars[i..].iter().collect();
            push(
                &mut tokens,
                TokenKind::Comment,
                text.clone(),
                col,
                col + text.chars().count(),
            );
            col += text.chars().count();
            i = chars.len();
            continue;
        }

        if c == '"' || c == '\'' || c == '`' {
            let quote = c;
            let start_col = col;
            let mut text = String::new();
            text.push(quote);
            i += 1;
            col += 1;
            while i < chars.len() {
                let ch = chars[i];
                text.push(ch);
                i += 1;
                col += 1;
                if ch == '\\' && i < chars.len() {
                    text.push(chars[i]);
                    i += 1;
                    col += 1;
                } else if ch == quote {
                    break;
                }
            }
            let kind = if quote == '\'' && language == Language::Java {
                TokenKind::CharLiteral
            } else {
                TokenKind::StringLiteral
            };
            push(&mut tokens, kind, text, start_col, col);
            continue;
        }

        if c.is_ascii_digit() || (c == '.' && i + 1 < chars.len() && chars[i + 1].is_ascii_digit())
        {
            let start_col = col;
            let mut num = String::new();
            while i < chars.len()
                && (chars[i].is_ascii_alphanumeric() || chars[i] == '.' || chars[i] == '_')
            {
                num.push(chars[i]);
                i += 1;
                col += 1;
            }
            push(&mut tokens, TokenKind::Number, num, start_col, col);
            continue;
        }

        if c == '@' && language == Language::Java {
            let start_col = col;
            let mut ann = String::from("@");
            i += 1;
            col += 1;
            while i < chars.len()
                && (chars[i].is_alphanumeric() || chars[i] == '_' || chars[i] == '.')
            {
                ann.push(chars[i]);
                i += 1;
                col += 1;
            }
            push(&mut tokens, TokenKind::Annotation, ann, start_col, col);
            continue;
        }

        if c.is_alphanumeric() || c == '_' || c == '$' {
            let start_col = col;
            let mut ident = String::new();
            while i < chars.len()
                && (chars[i].is_alphanumeric() || chars[i] == '_' || chars[i] == '$')
            {
                ident.push(chars[i]);
                i += 1;
                col += 1;
            }
            let is_call = i < chars.len() && chars[i] == '(';
            let kind = if language.keywords().contains(ident.as_str()) {
                TokenKind::Keyword
            } else if PRIMITIVE_TYPES.contains(ident.as_str()) {
                TokenKind::PrimitiveType
            } else if is_call {
                TokenKind::Function
            } else {
                TokenKind::Identifier
            };
            push(&mut tokens, kind, ident, start_col, col);
            continue;
        }

        push(
            &mut tokens,
            if "(){}[]".contains(c) {
                TokenKind::Separator
            } else {
                TokenKind::Operator
            },
            c.to_string(),
            col,
            col + 1,
        );
        i += 1;
        col += 1;
    }

    tokens
}

pub fn style_for_kind(kind: TokenKind, theme: &crate::config::Theme) -> Style {
    let color = match kind {
        TokenKind::Keyword => theme.keyword,
        TokenKind::PrimitiveType => theme.type_color,
        TokenKind::Identifier => theme.fg,
        TokenKind::Function => theme.function,
        TokenKind::StringLiteral | TokenKind::CharLiteral => theme.string,
        TokenKind::Comment => theme.comment,
        TokenKind::Number => theme.number,
        TokenKind::Operator => theme.operator,
        TokenKind::Separator => theme.fg,
        TokenKind::Annotation => theme.function,
        TokenKind::Whitespace | TokenKind::Unknown => theme.fg,
    };
    Style::default().fg(color)
}

pub fn render_highlighted_line(
    line: &str,
    language: Language,
    theme: &crate::config::Theme,
) -> Line<'static> {
    let tokens = highlight_line(line, language);
    let mut spans = Vec::new();
    for token in tokens {
        spans.push(Span::styled(token.text, style_for_kind(token.kind, theme)));
    }
    Line::from(spans)
}
