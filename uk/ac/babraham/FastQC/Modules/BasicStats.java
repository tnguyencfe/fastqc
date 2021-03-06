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
package uk.ac.babraham.FastQC.Modules;

import java.awt.BorderLayout;

import javax.swing.JLabel;
import javax.swing.JPanel;
import javax.swing.JScrollPane;
import javax.swing.JTable;
import javax.swing.table.AbstractTableModel;
import javax.swing.table.TableModel;

import uk.ac.babraham.FastQC.Report.HTMLReportArchive;
import uk.ac.babraham.FastQC.Sequence.Sequence;
import uk.ac.babraham.FastQC.Sequence.QualityEncoding.PhredEncoding;

public class BasicStats implements QCModule, QCModuleAggreg<BasicStats>{

	private String name = null;
	private int actualCount = 0;
	private int filteredCount = 0;
	private int minLength = 0;
	private int maxLength = 0;
	private long gCount = 0;
	private long cCount = 0;
	private long aCount = 0;
	private long tCount = 0;
	private long nCount = 0;
	private char lowestChar = 126;
	private String fileType = null;
	
	public BasicStats() {
		// default constructor
	}
	
	public BasicStats(String filename, String fileType) {
		this.name = filename;
		this.fileType = fileType;
	}
	
	public String description() {
		return "Calculates some basic statistics about the file";
	}
	
	public boolean ignoreFilteredSequences() {
		return false;
	}

	public JPanel getResultsPanel() {
		JPanel returnPanel = new JPanel();
		returnPanel.setLayout(new BorderLayout());
		returnPanel.add(new JLabel("Basic sequence stats",JLabel.CENTER),BorderLayout.NORTH);
		
		TableModel model = new ResultsTable();
		returnPanel.add(new JScrollPane(new JTable(model)),BorderLayout.CENTER);
		
		return returnPanel;
	
	}
	
	public void reset () {
		minLength = 0;
		maxLength = 0;
		gCount = 0;
		cCount = 0;
		aCount = 0;
		tCount = 0;
		nCount = 0;
	}

	public String name() {
		return "Basic Statistics";
	}
	
	public void processSequence(Sequence sequence) {

		if (name == null) name = sequence.file().name();
		
		// If this is a filtered sequence we simply count it and move on.
		if (sequence.isFiltered()) {
			filteredCount++;
			return;
		}
		
		actualCount++;
		
		if (fileType == null) {
			if (sequence.getColorspace() != null) {
				fileType = "Colorspace converted to bases";
			}
			else {
				fileType = "Conventional base calls";
			}
		}
		
		if (actualCount == 1) {
			minLength = sequence.getSequence().length();
			maxLength = sequence.getSequence().length();
		}
		else {
			if (sequence.getSequence().length() < minLength) minLength = sequence.getSequence().length();
			if (sequence.getSequence().length() > maxLength) maxLength = sequence.getSequence().length();
		}

		char [] chars = sequence.getSequence().toCharArray();
		for (int c=0;c<chars.length;c++) {			
			switch (chars[c]) {
				case 'G': ++gCount;break;
				case 'A': ++aCount;break;
				case 'T': ++tCount;break;
				case 'C': ++cCount;break;
				case 'N': ++nCount;break;			
			}
		}
		
		chars = sequence.getQualityString().toCharArray();
		for (int c=0;c<chars.length;c++) {
			if (chars[c] < lowestChar) {
				lowestChar = chars[c];
			}
		}
	}
	
	public boolean raisesError() {
		return false;
	}

	public boolean raisesWarning() {
		return false;
	}

	public void makeReport(HTMLReportArchive report) {
		ResultsTable table = new ResultsTable();
		
		StringBuffer b = report.htmlDocument();
		StringBuffer d = report.dataDocument();
		
		b.append("<table>\n");
		// Do the headers
		b.append("<tr>\n");
		d.append("#");
		for (int c=0;c<table.getColumnCount();c++) {
			b.append("<th>");
			b.append(table.getColumnName(c));
			d.append(table.getColumnName(c));
			b.append("</th>\n");
			d.append("\t");
		}
		b.append("</tr>\n");
		d.append("\n");
		
		// Do the rows
		for (int r=0;r<table.getRowCount();r++) {
			b.append("<tr>\n");
			for (int c=0;c<table.getColumnCount();c++) {
				b.append("<td>");
				b.append(table.getValueAt(r, c));
				d.append(table.getValueAt(r, c));
				b.append("</td>\n");
				d.append("\t");
			}
			b.append("</tr>\n");
			d.append("\n");
		}
		
		b.append("</table>\n");
				
		
	}

	private class ResultsTable extends AbstractTableModel {
				
		private String [] rowNames = new String [] {
				"Filename",
				"File type",
				"Encoding",
				"Total Sequences",
				"Filtered Sequences",
				"Sequence length",
				"%GC",
		};		
		
		// Sequence - Count - Percentage
		public int getColumnCount() {
			return 2;
		}
	
		public int getRowCount() {
			return rowNames.length;
		}
	
		public Object getValueAt(int rowIndex, int columnIndex) {
			switch (columnIndex) {
				case 0: return rowNames[rowIndex];
				case 1:
					switch (rowIndex) {
					case 0 : return name;
					case 1 : return fileType;
					case 2 : return PhredEncoding.getFastQEncodingOffset(lowestChar);
					case 3 : return ""+actualCount;
					case 4 : return ""+filteredCount;
					case 5 :
						if (minLength == maxLength) {
							return ""+minLength;
						}
						else {
							return minLength+"-"+maxLength;
						}
						
						
					case 6 : 
						if (aCount+tCount+gCount+cCount > 0) {
							return ""+(((gCount+cCount)*100)/(aCount+tCount+gCount+cCount));
						}
						else {
							return 0;
						}
					
					}
			}
			return null;
		}
		
		public String getColumnName (int columnIndex) {
			switch (columnIndex) {
				case 0: return "Measure";
				case 1: return "Value";
			}
			return null;
		}
		
		public Class<?> getColumnClass (int columnIndex) {
			switch (columnIndex) {
			case 0: return String.class;
			case 1: return String.class;
		}
		return null;
			
		}
	}

	@Override
	public synchronized void mergeResult(BasicStats result) {
		if (actualCount == 0) {
			minLength = result.minLength;
		}
		else {
			minLength = Math.min(minLength, result.minLength);
		}
		maxLength = Math.max(maxLength, result.maxLength);
		filteredCount += result.filteredCount;		
		actualCount += result.actualCount;		
		gCount += result.gCount;
		aCount += result.aCount;
		tCount += result.tCount;
		cCount += result.cCount;
		nCount += result.nCount;
		if (result.lowestChar < this.lowestChar) {
			
			lowestChar = result.lowestChar;
		}		
	}
	

}
