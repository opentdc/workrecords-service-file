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

import java.io.IOException;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.logging.Logger;

import javax.servlet.ServletContext;

import org.opentdc.file.AbstractFileServiceProvider;
import org.opentdc.service.exception.DuplicateException;
import org.opentdc.service.exception.InternalServerErrorException;
import org.opentdc.service.exception.NotFoundException;
import org.opentdc.service.exception.ValidationException;
import org.opentdc.util.PrettyPrinter;
import org.opentdc.workrecords.ServiceProvider;
import org.opentdc.workrecords.WorkRecordModel;

public class FileServiceProvider extends AbstractFileServiceProvider<WorkRecordModel> implements ServiceProvider {

	protected static Map<String, WorkRecordModel> index = new HashMap<String, WorkRecordModel>();
	protected static final Logger logger = Logger.getLogger(FileServiceProvider.class.getName());

	public FileServiceProvider(
		ServletContext context, 
		String prefix
	) throws IOException {
		super(context, prefix);
		if (index == null) {
			index = new HashMap<String, WorkRecordModel>();
			List<WorkRecordModel> _workRecords = importJson();
			for (WorkRecordModel _workRecord : _workRecords) {
				index.put(_workRecord.getId(), _workRecord);
			}
		}
	}

	@Override
	public ArrayList<WorkRecordModel> listWorkRecords(
		String queryType,
		String query,
		long position,
		long size
	) {
		logger.info("listWorkRecords() -> " + index.size() + " values");
		return new ArrayList<WorkRecordModel>(index.values());
	}

	@Override
	public WorkRecordModel createWorkRecord(
			WorkRecordModel workrecord) 
		throws DuplicateException, ValidationException {
		logger.info("createWorkRecord(" + PrettyPrinter.prettyPrintAsJSON(workrecord) + ")");
		String _id = workrecord.getId();
		if (_id == null || _id == "") {
			_id = UUID.randomUUID().toString();
		} else {
			if (index.get(_id) != null) {
				// object with same ID exists already
				throw new DuplicateException("workrecord <" + _id + 
						"> exists already.");				
			}
			else { // a new ID was set on the client; we do not allow this
				throw new ValidationException("workrecord <" + _id +
						"> contains an ID generated on the client. This is not allowed.");
			}
		}
		workrecord.setId(_id);
		index.put(_id, workrecord);
		logger.info("createWorkRecord() -> " + PrettyPrinter.prettyPrintAsJSON(workrecord));
		if (isPersistent) {
			exportJson(index.values());
		}
		return workrecord;
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
				exportJson(index.values());
			}
			return workrecord;
		}
	}

	@Override
	public void deleteWorkRecord(
			String id) 
		throws NotFoundException, InternalServerErrorException {
		WorkRecordModel _workrecord = index.get(id);
		if (_workrecord == null) {
			throw new NotFoundException("workRecord <" + id
					+ "> was not found.");
		}
		if (index.remove(id) == null) {
			throw new InternalServerErrorException("workRecord <" + id
					+ "> can not be removed, because it does not exist in the index.");
		}
		logger.info("deleteWorkRecord(" + id + ")");
		if (isPersistent) {
			exportJson(index.values());
		}
	}
}
