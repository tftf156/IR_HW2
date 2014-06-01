
/**
* Copyright 2005 The Apache Software Foundation
*
* Licensed under the Apache License, Version 2.0 (the "License");
* you may not use this file except in compliance with the License.
* You may obtain a copy of the License at
*
*     http://www.apache.org/licenses/LICENSE-2.0
*
* Unless required by applicable law or agreed to in writing, software
* distributed under the License is distributed on an "AS IS" BASIS,
* WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
* See the License for the specific language governing permissions and
* limitations under the License.
*/
import java.io.BufferedReader;
import java.io.File;
import java.io.FileFilter;
import java.io.FileReader;
import java.io.FileWriter;
import java.io.IOException;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Map;
import java.util.TreeMap;  
import java.util.Vector;

/**
* Split the Reuters SGML documents into Simple Text files containing: Title, Date, Dateline, Body
*/
public class ExtractReuters
{
	private File reutersDir;
	private File outputDir;
	private static final String LINE_SEPARATOR = System.getProperty("line.separator");
	//
	static ArrayList<Topics> allTopics;
	int docNumber = 0;
	int testStart = -1;
    private static List termsDocsArray = new ArrayList();
    private static List allTerms = new ArrayList();
    private static List tfidfDocsVector = new ArrayList();

	public ExtractReuters(File reutersDir, File outputDir)
	{
		this.reutersDir = reutersDir;
		this.outputDir = outputDir;
		System.out.println("Deleting all files in " + outputDir);
		File [] files = outputDir.listFiles();
		for (int i = 0; i < files.length; i++)
		{
			files[i].delete();
		}

	}

	public void extract()
	{
		allTopics = new ArrayList<>();
		File [] sgmFiles = reutersDir.listFiles(new FileFilter()
		{
			public boolean accept(File file)
			{
				return file.getName().endsWith(".sgm");
			}
		});
		if (sgmFiles != null && sgmFiles.length > 0)
		{
			docNumber = 0;
			for (int i = 0; i < sgmFiles.length; i++)
			{
				File sgmFile = sgmFiles[i];
				extractFile(sgmFile);
			}
			System.out.println("Complete!!");
			
			try {
				StringBuffer outBuffer = new StringBuffer(1024);
				
				for(Topics topics : allTopics)
				{
					outBuffer.append(topics.topicNumber);
					outBuffer.append(LINE_SEPARATOR).append(LINE_SEPARATOR);
					outBuffer.append(topics.bodyString);
					
					File outFile = new File(outputDir, topics.topicName + ".txt");
					FileWriter writer = new FileWriter(outFile);
					writer.write(outBuffer.toString());
					writer.close();
					outBuffer.setLength(0);
				}
				System.out.println("file complete");
			} catch (Exception e) {
				// TODO: handle exception
			}
			
			//
			//for(String topic : topicsArray)
			//{
			//	System.out.println(topic + " " + topicsNumArray.get(topicsArray.indexOf(topic)));
			//}
		}
		else
		{
			System.err.println("No .sgm files in " + reutersDir);
		}
	}

	Pattern EXTRACTION_PATTERN = Pattern.compile("<REUTERS (.*?)>|<TOPICS>(.*?)</TOPICS>|<TITLE>(.*?)</TITLE>|<BODY>(.*?)</BODY>");

	private static String[] META_CHARS = {"&", "<", ">", "\"", "'"};
	private static String[] META_CHARS_SERIALIZATIONS = {"&amp;", "&lt;", "&gt;", "&quot;", "&apos;"};

/**
* Override if you wish to change what is extracted
*
* @param sgmFile
*/
	protected void extractFile(File sgmFile)
	{
		try
		{
			BufferedReader reader = new BufferedReader(new FileReader(sgmFile));

			StringBuffer buffer = new StringBuffer(1024);
			StringBuffer outBuffer = new StringBuffer(1024);

			String line = null;
			int index = -1;
			int count = 0;
			String groupString;
			String typeString = "";
			String topicString = "";
			ArrayList<Integer> topicsIndex = new ArrayList<>();
			
			while ((line = reader.readLine()) != null)
			{
				//when we see a closing reuters tag, flush the file

				if ((index = line.indexOf("</REUTERS")) == -1)
				{
					//Replace the SGM escape sequences
					buffer.append(line).append(' ');//accumulate the strings for now, then apply regular expression to get the pieces,
				}
				else
				{

					Boolean topicBoolean = true;
					int matcherCount = 0;
					StringBuilder sb = new StringBuilder();
					topicsIndex.clear();
					//Extract the relevant pieces and write to a file in the output dir
					Matcher matcher = EXTRACTION_PATTERN.matcher(buffer);
					while (matcher.find())
					{
						groupString = matcher.group();
						if(docNumber == 16) System.out.println(groupString);
						String [] split = groupString.split(" ");
						//check REUTERS context
						if(split[0].equals("<REUTERS"))
						{
							//check is train or test
							typeString = split[2].substring(12, split[2].length() - 1);
							if(!(typeString.equals("TEST") || typeString.equals("TRAIN")))
							{
								topicBoolean = false;
								break;
							}
							if(typeString.equals("TEST") && testStart == -1)
							{
								testStart = docNumber;
								docNumber = 0;
							}
							
							//check topic, "YES" => storage, "NO" => ignore
							if(!split[1].substring(8, split[1].length() - 1).equals("YES"))
							{
								topicBoolean = false;
								break;
							}
						}
						//check topic is exist
						if(matcherCount == 1)
						{
							if(!groupString.substring(1, 6).equals("TOPIC"))
							{
								topicBoolean = false;
								break;
							}
							if(groupString.equals("<TOPICS></TOPICS>"))
							{
								topicBoolean = false;
								break;
							}
						}
						
						//check body is exist
						if(matcherCount == 3)
						{
							if(!groupString.substring(1, 5).equals("BODY"))
							{
								topicBoolean = false;
								break;
							}
							if(groupString.equals("<BODY></BODY>"))
							{
								topicBoolean = false;
								break;
							}
							else if(topicBoolean){
								String[] spliString = groupString.split("<BODY>");
								String bodyString = spliString[1].replaceAll("  Reuter &#3;</BODY>", "");
								bodyString = bodyString.replaceAll("  REUTER &#3;</BODY>", "");
								for (int i : topicsIndex) {
									allTopics.get(i).bodyString += bodyString;
								}

							}
						}
						//tfidf();
						
						for (int i = 1; i <= matcher.groupCount(); i++)
						{
							if (matcher.group(i) != null)
							{
								if(matcherCount == 1)
								{
									topicString = matcher.group(i);
									split = topicString.split("<D>");
									Integer length = split.length - 1;
									topicString = length.toString();
									for(int l=1;l<split.length;l++)
									{
										topicString = topicString + " " + split[l].split("</D>")[0];
										//
										String topicName = split[l].split("</D>")[0];
										boolean isContain = false;
										for(Topics topic : allTopics)
										{
											if(topicName.matches(topic.topicName))
											{
												isContain = true;
												int topicIndex = allTopics.indexOf(topic);
												topicsIndex.add(topicIndex);
												allTopics.get(topicIndex).topicNumber +=1;
											}
										}
										if(!isContain)
										{
											Topics topics = new Topics(topicName, 1);
											allTopics.add(topics);
											topicsIndex.add(allTopics.indexOf(topics));
										}
									}
									outBuffer.append(topicString);
									
								}
								else
									outBuffer.append(matcher.group(i));
							}
						}
						outBuffer.append(LINE_SEPARATOR).append(LINE_SEPARATOR);
						matcherCount++;
					}
					if(matcherCount < 4) topicBoolean = false;
					
					/*if(topicBoolean)
					{
						String out = outBuffer.toString();
						for (int i = 0; i < META_CHARS_SERIALIZATIONS.length; i++)
						{
							out = out.replaceAll(META_CHARS_SERIALIZATIONS[i], META_CHARS[i]);
						}
						File outFile = new File(outputDir, typeString + "-" + (docNumber++) + ".txt");
						//System.out.println("Writing " + outFile);
						FileWriter writer = new FileWriter(outFile);
						writer.write(out);
						writer.close();
						count++;
					}*/
					outBuffer.setLength(0);
					buffer.setLength(0);
					//if(count == 2)break;
				}
			}
			reader.close();
		}
		catch (IOException e)
		{
			throw new RuntimeException(e);
		}
	}
	public void tfidf() {
		double tf; //term frequency
        double idf; //inverse document frequency
        double tfidf; //term requency inverse document frequency        
        for (int i =0;i<termsDocsArray.size();i++) {
        	String[] array = new String[termsDocsArray.size()];
        	int index = 0;
        	for (Object value : termsDocsArray) {
        	  array[index] =  value.toString();
        	  index++;
        	}
            double[] tfidfvectors = new double[allTerms.size()];
            int count = 0;
            for (Object terms : allTerms) {
                tf = new TfIdf().tfCalculator(array, terms.toString());
                idf = new TfIdf().idfCalculator(termsDocsArray, terms.toString());
                tfidf = tf * idf;
                System.out.print(tfidf);
                tfidfvectors[count] = tfidf;
                count++;
            }
            System.out.print("\n------------------------\n");
            tfidfDocsVector.add(tfidfvectors);  //storing document vectors;            
        }
	}

	public static void main(String[] args)
	{
		File reutersDir = new File("../sgmFile");
		if (reutersDir.exists())
		{
			File outputDir = new File("../../output");
			outputDir.mkdirs();
			ExtractReuters extractor = new ExtractReuters(reutersDir, outputDir);
			extractor.extract();
			String bodyString = ""; 
			for (Topics topics : allTopics) {
				bodyString = topics.bodyString;
				String[] tokenizedTerms = bodyString.replaceAll("[\\W&&[^\\s]]", "").split("\\W+");
				System.out.print(bodyString);
				System.out.print("\n------------------------------\n");
				
				for (String term : tokenizedTerms) {
					
	                if (!allTerms.contains(term)) {  //avoid duplicate entry
	                    allTerms.add(term);
	                }
	            }
				
	            termsDocsArray.add(tokenizedTerms);
			}
			

		}
		else
		{
			printUsage();
		}
	}
	
	private static void printUsage()
	{
		System.err.println("Usage: java -cp <...> org.apache.lucene.benchmark.utils.ExtractReuters <Path to Reuters SGM files> <Output Path>");
	}
}