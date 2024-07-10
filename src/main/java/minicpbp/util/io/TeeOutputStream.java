package minicpbp.util.io;

import java.io.IOException;
import java.io.OutputStream;

//Create a custom output stream that writes to both file and console
public class TeeOutputStream extends OutputStream {
	private final OutputStream out1;
	private final OutputStream out2;

	public TeeOutputStream(OutputStream out1, OutputStream out2) {
		this.out1 = out1;
		this.out2 = out2;
	}

	@Override
	public void write(int b) throws IOException {
		out1.write(b);
		out2.write(b);
	}

	@Override
	public void flush() throws IOException {
		out1.flush();
		out2.flush();
	}

	@Override
	public void close() throws IOException {
		out1.close();
		out2.close();
	}
}