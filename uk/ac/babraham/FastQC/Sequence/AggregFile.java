package uk.ac.babraham.FastQC.Sequence;


import java.io.File;
import java.io.IOException;


public class AggregFile implements SequenceFile {

	private File file;
	private long fileSize = 0;	
	private String name;
	

	public AggregFile(File file) throws SequenceFormatException, IOException {
		this.file = file;
		fileSize = file.length();
		name = file.getName();
	}

	@Override
	public boolean hasNext() {
		return false;
	}

	@Override
	public Sequence next() throws SequenceFormatException {
		return null;
	}

	@Override
	public boolean isColorspace() {
		return false;
	}

	@Override
	public String name() {
		return this.name;
	}

	@Override
	public int getPercentComplete() {
		// TODO Auto-generated method stub
		return 0;
	}

	@Override
	public File getFile() {
		return file;
	}

}
