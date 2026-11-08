package alloverdev.exception;

public class InvalidOperationException extends RuntimeException {

    public InvalidOperationException() {
        super("illegal operation");
    }

    public InvalidOperationException(String m) {
        super(m);
    }
}
