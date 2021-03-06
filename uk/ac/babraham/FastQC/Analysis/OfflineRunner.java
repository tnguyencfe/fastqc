/**
 * Copyright Copyright 2010-12 Simon Andrews
 *
 *    This file is part of FastQC.
 *
 *    FastQC is free software; you can redistribute it and/or modify
 *    it under the terms of the GNU General Public License as published by
 *    the Free Software Foundation; either version 3 of the License, or
 *    (at your option) any later version.
 *
 *    FastQC is distributed in the hope that it will be useful,
 *    but WITHOUT ANY WARRANTY; without even the implied warranty of
 *    MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 *    GNU General Public License for more details.
 *
 *    You should have received a copy of the GNU General Public License
 *    along with FastQC; if not, write to the Free Software
 *    Foundation, Inc., 51 Franklin St, Fifth Floor, Boston, MA  02110-1301  USA
 */
package uk.ac.babraham.FastQC.Analysis;

import java.io.File;
import java.io.IOException;
import java.util.LinkedHashMap;
import java.util.Vector;

import uk.ac.babraham.FastQC.Modules.BasicStats;
import uk.ac.babraham.FastQC.Modules.KmerContent;
import uk.ac.babraham.FastQC.Modules.NContent;
import uk.ac.babraham.FastQC.Modules.OverRepresentedSeqs;
import uk.ac.babraham.FastQC.Modules.PerBaseGCContent;
import uk.ac.babraham.FastQC.Modules.PerBaseQualityScores;
import uk.ac.babraham.FastQC.Modules.PerBaseSequenceContent;
import uk.ac.babraham.FastQC.Modules.PerSequenceGCContent;
import uk.ac.babraham.FastQC.Modules.PerSequenceQualityScores;
import uk.ac.babraham.FastQC.Modules.QCModule;
import uk.ac.babraham.FastQC.Modules.QCModuleAggreg;
import uk.ac.babraham.FastQC.Modules.SequenceLengthDistribution;
import uk.ac.babraham.FastQC.Report.HTMLReportArchive;
import uk.ac.babraham.FastQC.Sequence.AggregSequenceFiles;
import uk.ac.babraham.FastQC.Sequence.SequenceFactory;
import uk.ac.babraham.FastQC.Sequence.SequenceFile;
import uk.ac.babraham.FastQC.Utilities.CasavaBasename;

public class OfflineRunner implements AnalysisListener {
	
	private int filesRemaining;
	private boolean showUpdates = true;
	private LinkedHashMap<Class, QCModuleAggreg> aggregModules; // maps the class to the module
	private AggregSequenceFiles aggregSeqFiles;
	
	
	
	public OfflineRunner (String [] filenames) {
		
		// See if we need to show updates
		if (System.getProperty("fastqc.quiet") != null && System.getProperty("fastqc.quiet").equals("true")) {
			showUpdates = false;
		}
		
		Vector<File> files = new Vector<File>();
		
		for (int f=0;f<filenames.length;f++) {
			File file = new File(filenames[f]);
			if (!file.exists() || ! file.canRead()) {
				System.err.println("Skipping '"+filenames[f]+"' which didn't exist, or couldn't be read");
				continue;
			}
			files.add(file);
		}
		
		File [][] fileGroups;
		
		// See if we need to group together files from a casava group
		if (System.getProperty("fastqc.casava") != null && System.getProperty("fastqc.casava").equals("true")) {
			fileGroups = CasavaBasename.getCasavaGroups(files.toArray(new File[0]));
		}
		else {
			fileGroups = new File [files.size()][1];
			for (int f=0;f<files.size();f++) {
				fileGroups[f][0] = files.elementAt(f);
			}
		}
		
		if (Boolean.getBoolean("fastqc.aggreg")) {
			try {
				String groupParentDirName = System.getProperty("fastqc.output_dir");
				String groupFilename = System.getProperty("fastqc.aggreg.file");				
				this.aggregSeqFiles = new AggregSequenceFiles(files.toArray(new File[0]), groupParentDirName, groupFilename);
				
				QCModuleAggreg[] modules = createQCAggregModules(aggregSeqFiles);			
				this.aggregModules = new LinkedHashMap<Class, QCModuleAggreg>(modules.length);			
				for (QCModuleAggreg module : modules) {
					this.aggregModules.put(module.getClass(), module);
				}	
			}
			catch(Exception ex) {
				System.out.println("Unable to aggregate statistics");
				ex.printStackTrace();
			}
		}
			
		filesRemaining = fileGroups.length;
		
		for (int i=0;i<fileGroups.length;i++) {

			try {
				processFile(fileGroups[i]);
			}
			catch (Exception e) {
				System.err.println("Failed to process "+fileGroups[i][0]);
				e.printStackTrace();
				--filesRemaining;
			}
		}
		
		// We need to hold this class open as otherwise the main method
		// exits when it's finished.
		while (filesRemaining > 0) {
			try {
				Thread.sleep(1000);
			} 
			catch (InterruptedException e) {}
		}
		
		if (Boolean.getBoolean("fastqc.aggreg") == true) {			
			aggregResults();
		}
		System.exit(0);
		
	}
	
	
	public void processFile (File [] files) throws Exception {
		for (int f=0;f<files.length;f++) {
			if (!files[f].exists()) {
				throw new IOException(files[f].getName()+" doesn't exist");
			}
		}
		SequenceFile sequenceFile;
		if (files.length == 1) {
			sequenceFile = SequenceFactory.getSequenceFile(files[0]);
		}		
		else {
			sequenceFile = SequenceFactory.getSequenceFile(files);
		}
						
		AnalysisRunner runner = new AnalysisRunner(sequenceFile);
		runner.addAnalysisListener(this);
		
		OverRepresentedSeqs os = new OverRepresentedSeqs();
		QCModule [] module_list = new QCModule [] { //TODO:  clean this up
				new BasicStats(),
				new PerBaseQualityScores(),
				new PerSequenceQualityScores(),
				new PerBaseSequenceContent(),
				new PerBaseGCContent(), 
				new PerSequenceGCContent(),
				new NContent(),
				new SequenceLengthDistribution(),
				os.duplicationLevelModule(),
				os,
				new KmerContent()
			};
		
		runner.startAnalysis(module_list);

	}	
		
	public void analysisComplete(SequenceFile file, QCModule[] results) {
		File reportFile;
		
		if (showUpdates) System.out.println("Analysis complete for "+file.name());

		
		if (System.getProperty("fastqc.output_dir") != null) {
			String fileName = file.getFile().getName().replaceAll(".gz$","").replaceAll(".bz2$","").replaceAll(".txt$","").replaceAll(".fastq$", "").replaceAll(".sam$", "").replaceAll(".bam$", "")+"_fastqc.zip";
			reportFile = new File(System.getProperty("fastqc.output_dir")+"/"+fileName);						
		}
		else {
			reportFile = new File(file.getFile().getAbsolutePath().replaceAll(".gz$","").replaceAll(".bz2$","").replaceAll(".txt$","").replaceAll(".fastq$", "").replaceAll(".sam$", "").replaceAll(".bam$", "")+"_fastqc.zip");			
		}
		
		try {
			new HTMLReportArchive(file, results, reportFile);
		}
		catch (Exception e) {
			analysisExceptionReceived(file, e);
			return;
		}
		
		if (Boolean.getBoolean("fastqc.aggreg") == true) {	
			for (int i = 0; i < results.length; i++) {
				QCModule result = results[i];
				if (aggregModules.containsKey(result.getClass())) {
					QCModuleAggreg aggregModule = aggregModules.get(result.getClass()); 
					aggregModule.mergeResult(result);
				}
				
			}
		}
		--filesRemaining;

	}
	
	/**
	 * Prints out the HTML for modules aggregated over all files
	 * @param comboFileName
	 */
	private void aggregResults() {
		try {
			if (this.aggregSeqFiles != null) {
				if (showUpdates)  {
					System.out.println("Generating aggregated results for all files");
				}
				new HTMLReportArchive(this.aggregSeqFiles.name(), aggregModules.values().toArray(new QCModule[0]), aggregSeqFiles.getOutputFile()); //TODO: order this so that it's in the same order each time
			}
			else {
				if (showUpdates)  {
					System.out.println("No aggregated results for all files");
				}
				return;
			}
		}
		catch (Exception e) {
			System.err.println("Failed to aggegate results for "+aggregSeqFiles.getOutputFile());
			e.printStackTrace();
			return;
		}
	}

	public void analysisUpdated(SequenceFile file, int sequencesProcessed, int percentComplete) {
		
		if (percentComplete % 5 == 0) {
			if (percentComplete == 105) {
				if (showUpdates) System.err.println("It seems our guess for the total number of records wasn't very good.  Sorry about that.");
			}
			if (percentComplete > 100) {
				if (showUpdates) System.err.println("Still going at "+percentComplete+"% complete for "+file.name());
			}
			else {
				if (showUpdates) System.err.println("Approx "+percentComplete+"% complete for "+file.name());
			}
		}
	}

	public void analysisExceptionReceived(SequenceFile file, Exception e) {
		System.err.println("Failed to process file "+file.name());
		e.printStackTrace();
		--filesRemaining;
	}

	public void analysisStarted(SequenceFile file) {
		if (showUpdates) System.err.println("Started analysis of "+file.name());
		
	}

	/**
	 * Convenience method to create array of QCModuleAggregs in a defined order.
	 * @return
	 */
	private QCModuleAggreg[] createQCAggregModules(AggregSequenceFiles seqFiles) {
		QCModuleAggreg [] module_list = new QCModuleAggreg[0];
		try {			
			StringBuffer seqFileNames = new StringBuffer(seqFiles.name());
			String nameSep = ":  ";
			seqFileNames.append(nameSep);
			for (File file : seqFiles.getInputFiles()) {
				if (seqFileNames.length() > seqFiles.name().length() + nameSep.length()) {
					seqFileNames.append(",");					
				}
				seqFileNames.append(file.getName());
			}
			
			OverRepresentedSeqs os = new OverRepresentedSeqs();
			module_list = new QCModuleAggreg [] {
				new BasicStats(seqFileNames.toString(), AggregSequenceFiles.getDescription()),
				new PerBaseQualityScores(),
				new PerSequenceQualityScores(),
				new PerBaseSequenceContent(),
				new PerBaseGCContent(), 
				new PerSequenceGCContent(),
				new NContent(),
				new SequenceLengthDistribution(),
				os.duplicationLevelModule(),
				os,
				new KmerContent()
			};
		}
		catch(Exception ex) {	
			System.out.println("Unable to aggregate statistics");
			ex.printStackTrace();
		}
		return module_list;
	}
}
