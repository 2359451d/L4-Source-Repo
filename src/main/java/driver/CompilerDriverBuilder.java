package driver;

import exception.PascalCompilerException;

import java.io.IOException;
import java.io.OutputStream;

public abstract class CompilerDriverBuilder {

    // default output stream
    private OutputStream out = System.out;

    public abstract CompilerDriverBuilder parse() throws IOException, PascalCompilerException;

    public abstract CompilerDriverBuilder check() throws PascalCompilerException, IOException;

    public void setOut(OutputStream out) {
        this.out = out;
    }

    public OutputStream getOut() {
        return out;
    }
}
