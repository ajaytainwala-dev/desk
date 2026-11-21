package desk.core.workspace;

/**
 * Unchecked exception for workspace persistence and open failures.
 */
public class WorkspaceException extends RuntimeException {

  public WorkspaceException(String message) {
    super(message);
  }

  public WorkspaceException(String message, Throwable cause) {
    super(message, cause);
  }
}
