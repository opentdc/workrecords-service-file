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
import java.util.Collections;
import java.util.Date;
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
		String query,
		String queryType,
		int position,
		int size
	) {
		ArrayList<WorkRecordModel> _workRecords = new ArrayList<WorkRecordModel>(index.values());
		Collections.sort(_workRecords, WorkRecordModel.WorkRecordComparator);
		ArrayList<WorkRecordModel> _selection = new ArrayList<WorkRecordModel>();
		for (int i = 0; i < _workRecords.size(); i++) {
			if (i >= position && i < (position + size)) {
				_selection.add(_workRecords.get(i));
			}			
		}
		logger.info("list(<" + query + ">, <" + queryType + 
				">, <" + position + ">, <" + size + ">) -> " + _selection.size() + " workrecords.");
		return _selection;
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
		// validate mandatory attributes
		if (workrecord.getCompanyId() == null || workrecord.getCompanyId().isEmpty()) {
			throw new ValidationException("workrecord <" + _id + 
					"> must contain a valid companyId.");
		}
		if (workrecord.getCompanyTitle() == null || workrecord.getCompanyTitle().isEmpty()) {
			throw new ValidationException("workrecord <" + _id + 
					"> must contain a valid companyTitle.");
		}
		if (workrecord.getProjectId() == null || workrecord.getProjectId().isEmpty()) {
			throw new ValidationException("workrecord <" + _id + 
					"> must contain a valid projectId.");
		}
		if (workrecord.getProjectTitle() == null || workrecord.getProjectTitle().isEmpty()) {
			throw new ValidationException("workrecord <" + _id + 
					"> must contain a valid projectTitle.");
		}
		if (workrecord.getResourceId() == null || workrecord.getResourceId().isEmpty()) {
			throw new ValidationException("workrecord <" + _id + 
					"> must contain a valid resourceId.");
		}
		if (workrecord.getStartAt() == null) {
			throw new ValidationException("workrecord <" + _id + 
					"> must contain a valid startAt date.");
		}
		workrecord.setId(_id);
		Date _date = new Date();
		workrecord.setCreatedAt(_date);
		workrecord.setCreatedBy(getPrincipal());
		workrecord.setModifiedAt(_date);
		workrecord.setModifiedBy(getPrincipal());
		index.put(_id, workrecord);
		logger.info("createWorkRecord() -> " + PrettyPrinter.prettyPrintAsJSON(workrecord));
		if (isPersistent) {
			exportJson(index.values());
		}
		return workrecord;
	}
	
	@Override
	public WorkRecordModel readWorkRecord(
			String id) 
			throws NotFoundException {
		WorkRecordModel _workrecord = index.get(id);
		if (_workrecord == null) {
			throw new NotFoundException("workrecord <" + id
					+ "> was not found.");
		}
		logger.info("readWorkRecord(" + id + ") -> " + _workrecord);
		return _workrecord;
	}

	@Override
	public WorkRecordModel updateWorkRecord(
		String id,
		WorkRecordModel workrecord
	) throws NotFoundException, ValidationException
	{
		logger.info("updateWorkRecord(" + id + ", " + PrettyPrinter.prettyPrintAsJSON(workrecord) + ")");
		WorkRecordModel _wrm = index.get(id);
		if(_wrm == null) {
			throw new NotFoundException("workrecord <" + id + "> was not found.");
		}
		if (! _wrm.getCreatedAt().equals(workrecord.getCreatedAt())) {
			logger.warning("workrecord <" + id + ">: ignoring createdAt value <" + 
					workrecord.getCreatedAt().toString() + "> because it was set on the client.");
		}
		if (! _wrm.getCreatedBy().equalsIgnoreCase(workrecord.getCreatedBy())) {
			logger.warning("workrecord <" + id + ">: ignoring createdBy value <" +
					workrecord.getCreatedBy() + ">: because it was set on the client.");		
		}
		if (! _wrm.getCompanyId().equalsIgnoreCase(workrecord.getCompanyId())) {
			throw new ValidationException("workrecord <" + id + 
					">: it is not allowed to change the companyId.");
		}
		if (! _wrm.getProjectId().equalsIgnoreCase(workrecord.getProjectId())) {
			throw new ValidationException("workrecord <" + id + 
					">: it is not allowed to change the projectId.");
		}
		if (! _wrm.getResourceId().equalsIgnoreCase(workrecord.getResourceId())) {
			throw new ValidationException("workrecord <" + id + 
					">: it is not allowed to change the resourceId.");
		}
		if (! _wrm.getCompanyTitle().equalsIgnoreCase(workrecord.getCompanyTitle())) {
			logger.warning("workrecord <" + id + ">: ignoring companyTitle value <" +
					workrecord.getCompanyTitle() + ">, because it is a derived attribute and can not be changed.");
		}
		if (! _wrm.getProjectTitle().equalsIgnoreCase(workrecord.getProjectTitle())) {
			logger.warning("workrecord <" + id + ">: ignoring projectTitle value <" +
					workrecord.getProjectTitle() + ">, because it is a derived attribute and can not be changed.");
		}
		_wrm.setStartAt(workrecord.getStartAt());
		_wrm.setDurationHours(workrecord.getDurationHours());
		_wrm.setDurationMinutes(workrecord.getDurationMinutes());
		_wrm.setBillable(workrecord.isBillable());
		_wrm.setComment(workrecord.getComment());
		_wrm.setModifiedAt(new Date());
		_wrm.setModifiedBy(getPrincipal());
		index.put(id, _wrm);
		logger.info("updateWorkRecord(" + id + ") -> " + PrettyPrinter.prettyPrintAsJSON(_wrm));
		if (isPersistent) {
			exportJson(index.values());
		}
		return _wrm;
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
