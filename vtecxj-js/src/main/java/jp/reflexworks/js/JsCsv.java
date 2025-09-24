package jp.reflexworks.js;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.StringReader;
import java.util.List;

import org.apache.commons.csv.CSVFormat;
import org.apache.commons.csv.CSVParser;
import org.apache.commons.csv.CSVRecord;

public class JsCsv {

	public String parsecsv(InputStream inputStream,String[] header,String[] items,String parent,int skip,String encoding) throws IOException {	
		return csv2json(parent, readcsv(inputStream, skip,encoding), items, header);
	}

	private String readcsv(InputStream inputStream,int skip,String encoding) throws IOException {

		BufferedReader reader = null;
		try {
			reader = new BufferedReader(new InputStreamReader(inputStream, encoding));

			StringBuilder sb = new StringBuilder();
			String str;
			boolean isFirst = true;
			while ((str = reader.readLine()) != null) {
				if (skip>0) {
					skip--;
					continue;
				}
				if (isFirst) {
					isFirst = false;
				} else {
					if (withoutQuote(str)) sb.append("\n");
				}
				sb.append(str.replaceAll("\t", ""));
			}
			return sb.toString();

		} finally {
			try {
				reader.close();
			} catch (Exception e) {
				// Do nothing.
			}
		}
	}

	private boolean withoutQuote(String target) {
        return (target.length() - target.replaceAll("\"", "").length())%2==0;
    }
	
	private String csv2json(String parentstr, String csv,String[] items,String[] header) throws IOException {

		StringReader br = new StringReader(csv);

		CSVParser parse = CSVFormat.EXCEL
				.withIgnoreEmptyLines(true)
				.withIgnoreSurroundingSpaces(true)
				.withCommentMarker('#')
				.parse(br);

		List<CSVRecord> recordList = parse.getRecords();

		StringBuilder json = new StringBuilder();

		json.append("{\"feed\":{\"entry\":[");

		if (recordList.size()>0) {
			CSVRecord record0 = recordList.get(0);
			// check header
			for(int i = 0; i < record0.size(); i++) {
				if (i>header.length-1) {
					//					json.append("{\"title\":\"Header columns unmatch."+record0.size()+":"+header.length+" recordheader="+record0.get(i)+"\"}]}}");
					//					return json.toString();	
					break;
				}
				if (!removeBom(record0.get(i)).equals(header[i].trim())) {
					json.append("{\"title\":\"Header parse error. record="+record0.get(i)+",header="+header[i]+"\"}]}}");
					return json.toString();
				}
			}

			String parent = "{\""+parentstr+"\":{";
			json.append(parent);

			for (int i=1;i<recordList.size();i++) {
				CSVRecord record = recordList.get(i);
				for (int j = 0; j < record.size(); j++) {
					if (j>=items.length) continue;
					json.append("\""+getItem(items[j])+"\":"+quote(items[j])+escape(record.get(j))+quote(items[j]));
					if ((j<record.size()-1)&&(j<items.length-1)) {
						json.append(",");
					}
				}
				if (i<recordList.size()-1) {
					json.append("}},"+parent);
				}
			}
			json.append("}}");
		}

		json.append("]}}");

		return json.toString();
	}

	private static String removeBom(String str) {
		if (str.startsWith("\uFEFF")) {
			str = str.substring(1);
		}
		return str;
	}

	private String escape(String string) {
		return string.replaceAll("\\\\", "¥").replaceAll("\"", "\\\\\"");
	}

	private String getItem(String item) {
		if (item.indexOf("(")>0) {
			return item.substring(0,item.indexOf("(") );
		}else {
			return item;
		}
	}

	private String quote(String item) {
		if ((item.indexOf("(int)")>0)|| (item.indexOf("(boolean)")>0)) {
			return "";
		}else {
			return "\"";
		}
	}

}
