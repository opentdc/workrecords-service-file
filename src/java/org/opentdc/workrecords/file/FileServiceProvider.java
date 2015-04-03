/**
 * The MIT License (MIT)
 *
 * Copyright (c) 2015 Arbalo AG
 *
 * Permission is hereby granted, free of charge, to any person obtaining a copy
 * of this software and associated documentation files (the "Software"), to deal
 * in the Software without restriction, including without limitation the rights
 * to use, copy, modify, merge, publish, distribute, sublicense, and/or sell
 * copies of the Software, and to permit persons to whom the Software is
 * furnished to do so, subject to the following conditions:
 *
 * The above copyright notice and this permission notice shall be included in all
 * copies or substantial portions of the Software.
 *
 * THE SOFTWARE IS PROVIDED "AS IS", WITHOUT WARRANTY OF ANY KIND, EXPRESS OR
 * IMPLIED, INCLUDING BUT NOT LIMITED TO THE WARRANTIES OF MERCHANTABILITY,
 * FITNESS FOR A PARTICULAR PURPOSE AND NONINFRINGEMENT. IN NO EVENT SHALL THE
 * AUTHORS OR COPYRIGHT HOLDERS BE LIABLE FOR ANY CLAIM, DAMAGES OR OTHER
 * LIABILITY, WHETHER IN AN ACTION OF CONTRACT, TORT OR OTHERWISE, ARISING FROM,
 * OUT OF OR IN CONNECTION WITH THE SOFTWARE OR THE USE OR OTHER DEALINGS IN THE
 * SOFTWARE.
 */
package org.opentdc.workrecords.file;

import java.io.File;
import java.io.FileInputStream;
import java.io.FileNotFoundException;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStreamReader;
import java.io.OutputStreamWriter;
import java.io.Reader;
import java.io.Writer;
import java.lang.reflect.Type;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.Map;
import java.util.logging.Logger;

import javax.servlet.ServletContext;

import org.opentdc.service.exception.DuplicateException;
import org.opentdc.service.exception.NotFoundException;
import org.opentdc.workrecords.ServiceProvider;
import org.opentdc.workrecords.WorkRecordModel;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.reflect.TypeToken;

public class FileServiceProvider implements ServiceProvider {
	
	private static final String SEED_FN = "/seed.json";
	private static final String DATA_FN = "/data.json";
	private static File dataF = null;
	private static File seedF = null;

	// instance variables
	private boolean isPersistent = true;

	protected static Map<String, WorkRecordModel> index = new HashMap<String, WorkRecordModel>();

	// instance variables
	protected static final Logger logger = Logger.getLogger(FileServiceProvider.class.getName());

	public FileServiceProvider(
		ServletContext context, 
		String prefix
	) {
		if (dataF == null) {
			dataF = new File(context.getRealPath("/" + prefix + DATA_FN));
		}
		if (seedF == null) {
			seedF = new File(context.getRealPath("/" + prefix + SEED_FN));
		}
		if (index.size() == 0) {
			importJson();
		}
	}

	@Override
	public ArrayList<WorkRecordModel> listWorkRecords(
		String queryType,
		String query,
		long position,
		long size
	) {
		logger.info("listWorkRecords() -> " + countWorkRecords() + " values");
		return new ArrayList<WorkRecordModel>(index.values());
	}

	@Override
	public WorkRecordModel createWorkRecord(WorkRecordModel workrecord) throws DuplicateException {
		logger.info("createWorkRecord(" + workrecord + ")");
		String _id = workrecord.getId();
		if (_id != null && _id != "" && index.get(workrecord.getId()) != null) {
			// object with same ID exists already
			throw new DuplicateException();
		}
		WorkRecordModel _workrecord = new WorkRecordModel(workrecord, true);
		index.put(_workrecord.getId(), _workrecord);
		if (isPersistent) {
			exportJson(dataF);
		}
		return _workrecord;
	}

	@Override
	public WorkRecordModel readWorkRecord(String id) throws NotFoundException {
		WorkRecordModel _workrecord = index.get(id);
		if (_workrecord == null) {
			throw new NotFoundException("no workrecord with ID <" + id
					+ "> was found.");
		}
		logger.info("readWorkRecord(" + id + ") -> " + _workrecord);
		return _workrecord;
	}

	@Override
	public WorkRecordModel updateWorkRecord(
		String id,
		WorkRecordModel workrecord
	) throws NotFoundException {
		if(index.get(id) == null) {
			throw new NotFoundException();
		} else {
			index.put(id, workrecord);
			logger.info("updateWorkRecord(" + workrecord + ")");
			if (isPersistent) {
				exportJson(dataF);
			}
			return workrecord;
		}
	}

	@Override
	public void deleteWorkRecord(String id) throws NotFoundException {
		WorkRecordModel _workrecord = index.get(id);
		;
		if (_workrecord == null) {
			throw new NotFoundException("deleteWorkRecord(" + id
					+ "): no such workrecord was found.");
		}
		index.remove(id);
		logger.info("deleteWorkRecord(" + id + ")");
		if (isPersistent) {
			exportJson(dataF);
		}
	}

	@Override
	public int countWorkRecords() {
		int _count = -1;
		if (index != null) {
			_count = index.values().size();
		}
		logger.info("countWorkRecords() = " + _count);
		return _count;
	}

	void importJson() {
		ArrayList<WorkRecordModel> _workrecords = null;

		// read the data file
		// either read persistent data from DATA_FN
		// or seed data from SEED_DATA_FN if no persistent data exists
		if (dataF.exists()) {
			logger.info("persistent data in file " + dataF.getName()
					+ " exists.");
			_workrecords = importJson(dataF);
		} else { // seeding the data
			logger.info("persistent data in file " + dataF.getName()
					+ " is missing -> seeding from " + seedF.getName());
			_workrecords = importJson(seedF);
		}
		// load the data into the local transient storage
		for (WorkRecordModel _workrecord : _workrecords) {
			index.put(_workrecord.getId(), _workrecord);
		}
		logger.info("added " + _workrecords.size() + " workrecords to index");

		if (isPersistent) {
			// create the persistent data if it did not exist
			if (!dataF.exists()) {
				try {
					dataF.createNewFile();
				} catch (IOException e) {
					logger.severe("importJson(): IO exception when creating file "
							+ dataF.getName());
					e.printStackTrace();
				}
				exportJson(dataF);
			}
		}
		logger.info("importJson(): imported " + _workrecords.size() + " workrecord objects");
	}

	/******************************** utility methods *****************************************/
	private ArrayList<WorkRecordModel> importJson(File f) throws NotFoundException {
		logger.info("importJson(" + f.getName() + "): importing WorkRecordsData");
		if (!f.exists()) {
			logger.severe("importJson(" + f.getName()
					+ "): file does not exist.");
			throw new NotFoundException("File " + f.getName()
					+ " does not exist.");
		}
		if (!f.canRead()) {
			logger.severe("importJson(" + f.getName()
					+ "): file is not readable");
			throw new NotFoundException("File " + f.getName()
					+ " is not readable.");
		}
		logger.info("importJson(" + f.getName() + "): can read the file.");

		Reader _reader = null;
		ArrayList<WorkRecordModel> _workrecords = null;
		try {
			_reader = new InputStreamReader(new FileInputStream(f));
			Gson _gson = new GsonBuilder().create();

			Type _collectionType = new TypeToken<ArrayList<WorkRecordModel>>() {
			}.getType();
			_workrecords = _gson.fromJson(_reader, _collectionType);
			logger.info("importJson(" + f.getName() + "): json data converted");
		} catch (FileNotFoundException e1) {
			logger.severe("importJson(" + f.getName()
					+ "): file does not exist (2).");
			e1.printStackTrace();
		} finally {
			try {
				if (_reader != null) {
					_reader.close();
				}
			} catch (IOException e) {
				logger.severe("importJson(" + f.getName()
						+ "): IOException when closing the reader.");
				e.printStackTrace();
			}
		}
		logger.info("importJson(" + f.getName() + "): " + _workrecords.size()
				+ " workrecords imported.");
		return _workrecords;
	}

	private void exportJson(File f) {
		logger.info("exportJson(" + f.getName() + "): exporting workrecords");
		Writer _writer = null;
		try {
			_writer = new OutputStreamWriter(new FileOutputStream(f));
			Gson _gson = new GsonBuilder().create();
			_gson.toJson(index.values(), _writer);
		} catch (FileNotFoundException e) {
			logger.severe("exportJson(" + f.getName() + "): file not found.");
			e.printStackTrace();
		} finally {
			if (_writer != null) {
				try {
					_writer.close();
				} catch (IOException e) {
					logger.severe("exportJson(" + f.getName()
							+ "): IOException when closing the reader.");
					e.printStackTrace();
				}
			}
		}
	}

}
