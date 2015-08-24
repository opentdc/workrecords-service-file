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
import java.util.StringTokenizer;
import java.util.UUID;
import java.util.logging.Logger;

import javax.servlet.ServletContext;
import javax.servlet.http.HttpServletRequest;

import org.opentdc.file.AbstractFileServiceProvider;
import org.opentdc.resources.ResourceModel;
import org.opentdc.service.exception.DuplicateException;
import org.opentdc.service.exception.InternalServerErrorException;
import org.opentdc.service.exception.NotFoundException;
import org.opentdc.service.exception.ValidationException;
import org.opentdc.util.LanguageCode;
import org.opentdc.util.PrettyPrinter;
import org.opentdc.workrecords.ServiceProvider;
import org.opentdc.service.ServiceUtil;
import org.opentdc.service.TagRefModel;
import org.opentdc.workrecords.TaggedWorkRecord;
import org.opentdc.workrecords.WorkRecordModel;
import org.opentdc.workrecords.WorkRecordQueryHandler;
import org.opentdc.wtt.CompanyModel;
import org.opentdc.wtt.ProjectModel;

public class FileServiceProvider extends AbstractFileServiceProvider<TaggedWorkRecord> implements ServiceProvider {

	protected static Map<String, TaggedWorkRecord> index = null;
	protected static Map<String, TagRefModel> tagRefIndex = null;
	protected static Logger logger = null;
	protected static boolean isResourceDerived = true;

	/**
	 * Constructor.
	 * @param context the servlet context (for config)
	 * @param prefix the directory name where the seed and data.json reside (typically the classname of the service)
	 * @throws IOException
	 */
	public FileServiceProvider(
		ServletContext context, 
		String prefix
	) throws IOException {
		super(context, prefix);
		if (index == null) {
			index = new HashMap<String, TaggedWorkRecord>();
			List<TaggedWorkRecord> _workRecords = importJson();
			for (TaggedWorkRecord _workRecord : _workRecords) {
				index.put(_workRecord.getModel().getId(), _workRecord);
			}
			tagRefIndex = new HashMap<String, TagRefModel>();
			String _buf = context.getInitParameter("isResourceDerived");
			logger = Logger.getLogger(FileServiceProvider.class.getName());
			logger.info("init parameter <isResourceDerived> (_buf)=<" + _buf + ">");
			isResourceDerived = Boolean.parseBoolean(context.getInitParameter("isResourceDerived"));
		}
		logger.info("isResourceDerived=<" + isResourceDerived + ">") ;
	}

	/* (non-Javadoc)
	 * @see org.opentdc.workrecords.ServiceProvider#listWorkRecords(java.lang.String, java.lang.String, int, int)
	 */
	@Override
	public ArrayList<WorkRecordModel> listWorkRecords(
		String query,
		String queryType,
		int position,
		int size) 
	{
		List<TaggedWorkRecord> _list = new ArrayList<TaggedWorkRecord>(index.values());
		Collections.sort(_list, TaggedWorkRecord.TaggedWorkRecordComparator);
		WorkRecordQueryHandler _queryHandler = new WorkRecordQueryHandler(query);
		ArrayList<WorkRecordModel> _selection = new ArrayList<WorkRecordModel>();
		for (int i = 0; i < _list.size(); i++) {
			if (i >= position && i < (position + size)) {
				if (_queryHandler.evaluate(_list.get(i)) == true) {
					_selection.add(_list.get(i).getModel());
				}
			}			
		}
		logger.info("list(<" + query + ">, <" + queryType + 
				">, <" + position + ">, <" + size + ">) -> " + _selection.size() + " workrecords.");
		return _selection;
	}

	/* (non-Javadoc)
	 * @see org.opentdc.workrecords.ServiceProvider#createWorkRecord(org.opentdc.workrecords.WorkRecordModel)
	 */
	@Override
	public WorkRecordModel createWorkRecord(
			HttpServletRequest request,
			WorkRecordModel workrecord) 
		throws DuplicateException, ValidationException 
	{
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
		if (workrecord.getCompanyTitle() != null && !workrecord.getCompanyTitle().isEmpty()) {
			logger.warning("workrecord <" + _id +  
					">: companyTitle is a derived field and will be overwritten.");
		}
		try {
			CompanyModel _companyModel = org.opentdc.wtt.file.FileServiceProvider.getCompany(workrecord.getCompanyId());
			workrecord.setCompanyTitle(_companyModel.getTitle());			
		}
		catch (NotFoundException _ex) {
			throw new ValidationException("workrecord <" + _id + 
					"> contains an invalid companyId <" + workrecord.getCompanyId() + ">.");
		}

		if (workrecord.getProjectId() == null || workrecord.getProjectId().isEmpty()) {
			throw new ValidationException("workrecord <" + _id + 
					"> must contain a valid projectId.");
		}
		if (workrecord.getProjectTitle() != null && !workrecord.getProjectTitle().isEmpty()) {
			logger.warning("workrecord <" + _id +  
					">: projectTitle is a derived field and will be overwritten.");
		}
		try {
			ProjectModel _projectModel = org.opentdc.wtt.file.FileServiceProvider.getProject(workrecord.getProjectId());
			workrecord.setProjectTitle(_projectModel.getTitle());
		}
		catch (NotFoundException _ex) {
			throw new ValidationException("workrecord <" + _id + 
					"> contains an invalid projectId <" + workrecord.getProjectId() + ">.");
		}
		
		if (workrecord.getResourceId() == null || workrecord.getResourceId().isEmpty()) {
			throw new ValidationException("workrecord <" + _id + 
					"> must contain a valid resourceId.");
		}
		if (isResourceDerived == true) {
			if (workrecord.getResourceName() != null && !workrecord.getResourceName().isEmpty()) {
				logger.warning("workrecord <" + _id +  
					">: resourceName is a derived field and will be overwritten.");
			}
			try {
				ResourceModel _resourceModel = org.opentdc.resources.file.FileServiceProvider.getResourceModel(workrecord.getResourceId());
				workrecord.setResourceName(_resourceModel.getName());
			}
			catch (NotFoundException _ex) {
				throw new ValidationException("workrecord <" + _id + 
					"> contains an invalid resourceId <" + workrecord.getResourceId() + ">.");
			}
		} else {		// TODO: this is a workaround for the Arbalo demo; resourceName is not derived, but a mandatory field
			if (workrecord.getResourceName() == null || workrecord.getResourceName().isEmpty()) {
				throw new ValidationException("workrecord <" + _id + 
						"> must contain a valid resourceName.");
			}
		}
		
		if (workrecord.getStartAt() == null) {
			throw new ValidationException("workrecord <" + _id + 
					"> must contain a valid startAt date.");
		}
		workrecord.setId(_id);
		Date _date = new Date();
		workrecord.setCreatedAt(_date);
		workrecord.setCreatedBy(ServiceUtil.getPrincipal(request));
		workrecord.setModifiedAt(_date);
		workrecord.setModifiedBy(ServiceUtil.getPrincipal(request));
		TaggedWorkRecord _taggedWR = new TaggedWorkRecord();
		_taggedWR.setModel(workrecord);
		index.put(_id, _taggedWR);
		
		// generate TagRefModel composites
		addTags(request, _id, workrecord.getTagIdList());
		
		logger.info("createWorkRecord() -> " + PrettyPrinter.prettyPrintAsJSON(workrecord));
		if (isPersistent) {
			exportJson(index.values());
		}
		return workrecord;
	}
	
	/* (non-Javadoc)
	 * @see org.opentdc.workrecords.ServiceProvider#readWorkRecord(java.lang.String)
	 */
	@Override
	public WorkRecordModel readWorkRecord(
			String id) 
			throws NotFoundException {
		WorkRecordModel _workrecord = readTaggedWorkRecord(id).getModel();
		logger.info("readWorkRecord(" + id + ") -> " + _workrecord);
		return _workrecord;
	}
	
	/**
	 * Retrieve the TaggedWorkRecord based on the id of the WorkRecord.
	 * @param id the unique id of the resource
	 * @return the TaggedWorkRecord that contains the WorkRecord as its model
	 * @throws NotFoundException if no workrecord with this id was found
	 */
	private static TaggedWorkRecord readTaggedWorkRecord(
			String id)
			throws NotFoundException {
		TaggedWorkRecord _taggedWR = index.get(id);
		if (_taggedWR == null) {
			throw new NotFoundException("no workrecord with id <" + id + "> was found.");			
		}
		logger.info("readTaggedWorkRecord(" + id + ") -> " + PrettyPrinter.prettyPrintAsJSON(_taggedWR));
		return _taggedWR;
	}
	
	/**
	 * Retrieve a WorkRecordModel by its ID.
	 * @param id the ID of the workrecord
	 * @return the workrecord found
	 * @throws NotFoundException if no workrecords with such an ID was found
	 */
	public static WorkRecordModel getWorkRecordModel(
			String id) 
			throws NotFoundException {
		return readTaggedWorkRecord(id).getModel();
	}


	/* (non-Javadoc)
	 * @see org.opentdc.workrecords.ServiceProvider#updateWorkRecord(java.lang.String, org.opentdc.workrecords.WorkRecordModel)
	 */
	@Override
	public WorkRecordModel updateWorkRecord(
		HttpServletRequest request,
		String id,
		WorkRecordModel workrecord) 
				throws NotFoundException, ValidationException
	{
		TaggedWorkRecord _taggedWR = readTaggedWorkRecord(id);
		WorkRecordModel _model = _taggedWR.getModel();		
		if(_model == null) {
			throw new NotFoundException("workrecord <" + id + "> was not found.");
		}
		if (! _model.getCreatedAt().equals(workrecord.getCreatedAt())) {
			logger.warning("workrecord <" + id + ">: ignoring createdAt value <" + 
					workrecord.getCreatedAt().toString() + "> because it was set on the client.");
		}
		if (! _model.getCreatedBy().equalsIgnoreCase(workrecord.getCreatedBy())) {
			logger.warning("workrecord <" + id + ">: ignoring createdBy value <" +
					workrecord.getCreatedBy() + ">: because it was set on the client.");		
		}
		// company (id and title)
		if (! _model.getCompanyId().equalsIgnoreCase(workrecord.getCompanyId())) {
			throw new ValidationException("workrecord <" + id + 
					">: it is not allowed to change the companyId.");
		}
		if (! _model.getCompanyTitle().equalsIgnoreCase(workrecord.getCompanyTitle())) {
			logger.warning("workrecord <" + id + ">: ignoring companyTitle value <" +
					workrecord.getCompanyTitle() + ">, because it is a derived attribute and can not be changed.");
		}
		try {
			CompanyModel _companyModel = org.opentdc.wtt.file.FileServiceProvider.getCompany(workrecord.getCompanyId());
			_model.setCompanyTitle(_companyModel.getTitle());			
		}
		catch (NotFoundException _ex) {
			throw new ValidationException("workrecord <" + id + 
					"> contains an invalid companyId <" + workrecord.getCompanyId() + ">.");
		}
		
		// project (id and title)
		if (! _model.getProjectId().equalsIgnoreCase(workrecord.getProjectId())) {
			throw new ValidationException("workrecord <" + id + 
					">: it is not allowed to change the projectId.");
		}
		if (! _model.getProjectTitle().equalsIgnoreCase(workrecord.getProjectTitle())) {
			logger.warning("workrecord <" + id + ">: ignoring projectTitle value <" +
					workrecord.getProjectTitle() + ">, because it is a derived attribute and can not be changed.");
		}
		try {
			ProjectModel _projectModel = org.opentdc.wtt.file.FileServiceProvider.getProject(workrecord.getProjectId());
			_model.setProjectTitle(_projectModel.getTitle());
		}
		catch (NotFoundException _ex) {
			throw new ValidationException("workrecord <" + id + 
					"> contains an invalid projectId <" + workrecord.getProjectId() + ">.");
		}
		
		// resource (id and name)
		if (! _model.getResourceId().equalsIgnoreCase(workrecord.getResourceId())) {
			throw new ValidationException("workrecord <" + id + 
					">: it is not allowed to change the resourceId.");
		}
		if (isResourceDerived == true) {
			if (! _model.getResourceName().equalsIgnoreCase(workrecord.getResourceName())) {
				logger.warning("workrecord <" + id + ">: ignoring resourceName value <" +
						workrecord.getResourceName() + ">, because it is a derived attribute and can not be changed.");
			}
			try {
				ResourceModel _resourceModel = org.opentdc.resources.file.FileServiceProvider.getResourceModel(workrecord.getResourceId());
				_model.setResourceName(_resourceModel.getName());
			}
			catch (NotFoundException _ex) {
				throw new ValidationException("workrecord <" + id + 
						"> contains an invalid resourceId <" + workrecord.getResourceId() + ">.");
			}
		}
		else {		// TODO: workaround for Arbalo event: resourceName is mandatory attribute (instead of derived)
			if (_model.getResourceName() == null || _model.getResourceName().length() == 0) {
				throw new ValidationException("updating resource <" + id + ">: new data must have a valid resourceName.");
			}
		}

		_model.setStartAt(workrecord.getStartAt());
		_model.setDurationHours(workrecord.getDurationHours());
		_model.setDurationMinutes(workrecord.getDurationMinutes());
		_model.setBillable(workrecord.isBillable());
		_model.setRunning(workrecord.isRunning());
		_model.setPaused(workrecord.isPaused());
		_model.setComment(workrecord.getComment());
		_model.setModifiedAt(new Date());
		_model.setModifiedBy(ServiceUtil.getPrincipal(request));
		_taggedWR.setModel(_model);
		index.put(id, _taggedWR);
		logger.info("updateWorkRecord(" + id + ") -> " + PrettyPrinter.prettyPrintAsJSON(_model));
		if (isPersistent) {
			exportJson(index.values());
		}
		return _model;
	}

	@Override
	public void deleteWorkRecord(
			String id) 
		throws NotFoundException, InternalServerErrorException {
		TaggedWorkRecord _taggedWR = readTaggedWorkRecord(id);
		// remove all tagRefs of this TaggedWorkRecord from tagRefIndex	
		for (TagRefModel _tagRef : _taggedWR.getTagRefs()) {
			if (tagRefIndex.remove(_tagRef.getId()) == null) {
				throw new InternalServerErrorException("tagRef <" + _tagRef.getId()
						+ "> can not be removed, because it does not exist in the tagRefIndex");
			}
		}
		if (index.remove(id) == null) {
			throw new InternalServerErrorException("workRecord <" + id
					+ "> can not be removed, because it does not exist in the index.");
		}
		logger.info("deleteWorkRecord(" + id + ") -> OK");
		if (isPersistent) {
			exportJson(index.values());
		}
	}

	/************************************** TagRef ************************************/
	/* (non-Javadoc)
	 * @see org.opentdc.workrecords.ServiceProvider#listTagRefs(java.lang.String, java.lang.String, java.lang.String, int, int)
	 */
	@Override
	public List<TagRefModel> listTagRefs(
			String id, 
			String query,
			String queryType, 
			int position, 
			int size) 
	{
		List<TagRefModel> _tags = readTaggedWorkRecord(id).getTagRefs();
		Collections.sort(_tags, TagRefModel.TagRefComparator);
		
		ArrayList<TagRefModel> _selection = new ArrayList<TagRefModel>();
		for (int i = 0; i < _tags.size(); i++) {
			if (i >= position && i < (position + size)) {
				_selection.add(_tags.get(i));
			}
		}
		logger.info("listTagRefs(<" + id + ">, <" + queryType + ">, <" + query + 
				">, <" + position + ">, <" + size + ">) -> " + _selection.size()
				+ " values");
		return _selection;
	}
	
	/* (non-Javadoc)
	 * @see org.opentdc.workrecords.ServiceProvider#createTagRef(java.lang.String, org.opentdc.workrecords.TagRefModel)
	 */
	@Override
	public TagRefModel createTagRef(
			HttpServletRequest request,
			String workRecordId, 
			TagRefModel model)
			throws DuplicateException, ValidationException 
	{
		TaggedWorkRecord _taggedWR = readTaggedWorkRecord(workRecordId);
		if (model.getTagId() == null || model.getTagId().isEmpty()) {
			throw new ValidationException("TagRef in WorkRecord <" + workRecordId + "> must contain a valid tagId.");
		}
		// a tag can be contained as a TagRef within a WorkRecord 0 or 1 times
		if (_taggedWR.containsTag(model.getTagId())) {
			throw new DuplicateException("TagRef with Tag <" + model.getTagId() + 
					"> exists already in WorkRecord <" + workRecordId + ">.");
		}
		
		if (lang == null) {
			logger.warning("lang is null; using default");
			lang = LanguageCode.getDefaultLanguageCode();
		}
		String _id = model.getId();
		if (_id == null || _id.isEmpty()) {
			_id = UUID.randomUUID().toString();
		} else {
			if (tagRefIndex.get(_id) != null) {
				throw new DuplicateException("TagRef with id <" + _id + 
						"> exists already in tagRefIndex.");
			}
			else {
				throw new ValidationException("TagRef with id <" + _id +
						"> contains an ID generated on the client. This is not allowed.");
			}
		}

		model.setId(_id);
		model.setCreatedAt(new Date());
		model.setCreatedBy(ServiceUtil.getPrincipal(request));
		
		tagRefIndex.put(_id, model);
		_taggedWR.addTagRef(model);
		
		logger.info("createTagRef(" + workRecordId + ") -> " + PrettyPrinter.prettyPrintAsJSON(model));
		if (isPersistent) {
			exportJson(index.values());
		}
		return model;
	}

	/* (non-Javadoc)
	 * @see org.opentdc.workrecords.ServiceProvider#readTagRef(java.lang.String, java.lang.String)
	 */
	@Override
	public TagRefModel readTagRef(
			String workRecordId, 
			String tagRefId)
			throws NotFoundException 
	{
		readTaggedWorkRecord(workRecordId);		// verify that the workrecord exists
		TagRefModel _tagRef = tagRefIndex.get(tagRefId);
		if (_tagRef == null) {
			throw new NotFoundException("TagRef <" + workRecordId + "/tagref/" + tagRefId +
					"> was not found.");
		}
		logger.info("readRateRef(" + workRecordId + ", " + tagRefId + ") -> "
				+ PrettyPrinter.prettyPrintAsJSON(_tagRef));
		return _tagRef;
	}

	/* (non-Javadoc)
	 * @see org.opentdc.workrecords.ServiceProvider#deleteTagRef(java.lang.String, java.lang.String)
	 */
	@Override
	public void deleteTagRef(
			String workRecordId, 
			String tagRefId)
			throws NotFoundException, InternalServerErrorException 
	{
		TaggedWorkRecord _taggedWR = readTaggedWorkRecord(workRecordId);
		TagRefModel _tagRef = tagRefIndex.get(tagRefId);
		if (_tagRef == null) {
			throw new NotFoundException("TagRef <" + workRecordId + "/tagref/" + tagRefId +
					"> was not found.");
		}
		
		// 1) remove the TagRef from its Resource
		if (_taggedWR.removeTagRef(_tagRef) == false) {
			throw new InternalServerErrorException("TagRef <" + workRecordId + "/tagref/" + tagRefId
					+ "> can not be removed, because it is an orphan.");
		}
		// 2) remove the TagRef from the tagRefIndex
		if (tagRefIndex.remove(_tagRef.getId()) == null) {
			throw new InternalServerErrorException("TagRef <" + workRecordId + "/tagref/" + tagRefId
					+ "> can not be removed, because it does not exist in the index.");
		}	
		logger.info("deleteTagRef(" + workRecordId + ", " + tagRefId + ") -> OK");
		if (isPersistent) {
			exportJson(index.values());
		}				
	}

	// format of String tagIdList ::=  tagId{.tagId}
	@Override
	public List<TagRefModel> addTags(
			HttpServletRequest request,
			String workRecordId, 
			String tagIdList) 
	{
		ArrayList<TagRefModel> _tagRefs = new ArrayList<TagRefModel>();
		if (tagIdList != null && !tagIdList.isEmpty()) {
			StringTokenizer _st = new StringTokenizer(tagIdList, ",");
			while (_st.hasMoreTokens()) {
				_tagRefs.add(createTagRef(request, workRecordId, new TagRefModel(_st.nextToken())));
			}
		}
		return _tagRefs;
	}
}
