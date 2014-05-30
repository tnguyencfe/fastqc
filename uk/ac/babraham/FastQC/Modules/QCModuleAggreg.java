package uk.ac.babraham.FastQC.Modules;

public interface QCModuleAggreg <T extends QCModule>{
	public void mergeResult(T result);
}
