package uk.ac.babraham.FastQC.Sequence;

import java.io.File;

public class AggregSequenceFiles {

	private File [] files;
	private String groupName = null;
	private File groupFile = null;
	
	/** 
	 * Create a AggregSequenceFiles of a set of files that are not part of the same casava group.
	 * EG)  files from multiple samples or multiple runs.
	 * @param files  
	 * @param groupParentDir since the files don't have to be part of the same run, we need to know where the group output files should go.
	 * 						Default:  parent directory of first file in group
	 * @param groupName arbitrary group name to identify group
	 * 						Default:  AggregatedResults_{first file in group}_to_{last file in group}
	 */
	public AggregSequenceFiles (File [] files, String groupParentDirPath, String groupName) {
		this.files = files;
		this.groupName = groupName;
		
		if (groupParentDirPath == null || groupParentDirPath.trim().length() == 0) {
			groupParentDirPath = files[0].getParent();
		}
		

		if (groupName == null || groupName.trim().length() == 0) {
			groupName = "AggregatedResults_" + files[0].getName() + "_to_" + files[files.length-1].getName();
		}
		
		groupFile = new File(groupParentDirPath + File.separator + groupName + "_fastqc.zip");		
	}
	

	public File[] getInputFiles() {
		return this.files;
	}


	public String name() {		
		return groupName;
	}


	public File getOutputFile() {
		return groupFile;
	}
	
	public static String getDescription() {
		return "Aggregated Statistics";
	}
	
	

}
