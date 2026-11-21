package desk.java.lsp;

public record CompletionItem(
    String label,
    String detail,
    String insertText,
    String kind) {
}
