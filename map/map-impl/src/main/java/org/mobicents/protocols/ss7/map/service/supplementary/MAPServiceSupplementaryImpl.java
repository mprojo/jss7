/*
 * JBoss, Home of Professional Open Source
 * Copyright 2011, Red Hat, Inc. and individual contributors
 * by the @authors tag. See the copyright.txt in the distribution for a
 * full listing of individual contributors.
 *
 * This is free software; you can redistribute it and/or modify it
 * under the terms of the GNU Lesser General Public License as
 * published by the Free Software Foundation; either version 2.1 of
 * the License, or (at your option) any later version.
 *
 * This software is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE. See the GNU
 * Lesser General Public License for more details.
 *
 * You should have received a copy of the GNU Lesser General Public
 * License along with this software; if not, write to the Free
 * Software Foundation, Inc., 51 Franklin St, Fifth Floor, Boston, MA
 * 02110-1301 USA, or see the FSF site: http://www.fsf.org.
 */

package org.mobicents.protocols.ss7.map.service.supplementary;

import org.apache.log4j.Logger;
import org.mobicents.protocols.asn.AsnInputStream;
import org.mobicents.protocols.asn.Tag;
import org.mobicents.protocols.ss7.map.MAPDialogImpl;
import org.mobicents.protocols.ss7.map.MAPProviderImpl;
import org.mobicents.protocols.ss7.map.MAPServiceBaseImpl;
import org.mobicents.protocols.ss7.map.api.MAPApplicationContext;
import org.mobicents.protocols.ss7.map.api.MAPApplicationContextName;
import org.mobicents.protocols.ss7.map.api.MAPDialog;
import org.mobicents.protocols.ss7.map.api.MAPException;
import org.mobicents.protocols.ss7.map.api.MAPOperationCode;
import org.mobicents.protocols.ss7.map.api.MAPParsingComponentException;
import org.mobicents.protocols.ss7.map.api.MAPParsingComponentExceptionReason;
import org.mobicents.protocols.ss7.map.api.MAPServiceListener;
import org.mobicents.protocols.ss7.map.api.dialog.ServingCheckData;
import org.mobicents.protocols.ss7.map.api.dialog.ServingCheckResult;
import org.mobicents.protocols.ss7.map.api.primitives.AddressString;
import org.mobicents.protocols.ss7.map.api.service.supplementary.MAPDialogSupplementary;
import org.mobicents.protocols.ss7.map.api.service.supplementary.MAPServiceSupplementary;
import org.mobicents.protocols.ss7.map.api.service.supplementary.MAPServiceSupplementaryListener;
import org.mobicents.protocols.ss7.map.dialog.ServingCheckDataImpl;
import org.mobicents.protocols.ss7.map.service.sms.MAPServiceSmsImpl;
import org.mobicents.protocols.ss7.sccp.parameter.SccpAddress;
import org.mobicents.protocols.ss7.tcap.api.tc.dialog.Dialog;
import org.mobicents.protocols.ss7.tcap.asn.ApplicationContextName;
import org.mobicents.protocols.ss7.tcap.asn.TcapFactory;
import org.mobicents.protocols.ss7.tcap.asn.comp.ComponentType;
import org.mobicents.protocols.ss7.tcap.asn.comp.Invoke;
import org.mobicents.protocols.ss7.tcap.asn.comp.OperationCode;
import org.mobicents.protocols.ss7.tcap.asn.comp.Parameter;

/**
 * 
 * @author amit bhayani
 * @author baranowb
 * @author sergey vetyutnev
 * 
 */
public class MAPServiceSupplementaryImpl extends MAPServiceBaseImpl implements MAPServiceSupplementary {

	private static final Logger loger = Logger.getLogger(MAPServiceSmsImpl.class);

	public MAPServiceSupplementaryImpl(MAPProviderImpl mapProviderImpl) {
		super(mapProviderImpl);
	}

	/**
	 * Creating a new outgoing MAP Supplementary dialog and adding it to the
	 * MAPProvider.dialog collection
	 * 
	 */
	public MAPDialogSupplementary createNewDialog(MAPApplicationContext appCntx, SccpAddress origAddress, AddressString origReference, SccpAddress destAddress,
			AddressString destReference) throws MAPException {

		// We cannot create a dialog if the service is not activated
		if (!this.isActivated())
			throw new MAPException("Cannot create MAPDialogSupplementary because MAPServiceSupplementary is not activated");

		Dialog tcapDialog = this.createNewTCAPDialog(origAddress, destAddress);
		MAPDialogSupplementaryImpl dialog = new MAPDialogSupplementaryImpl(appCntx, tcapDialog, this.mapProviderImpl, this, origReference, destReference);

		this.PutMADDialogIntoCollection(dialog);

		return dialog;
	}

	/*
	 * (non-Javadoc)
	 * 
	 * @see
	 * org.mobicents.protocols.ss7.map.MAPServiceBaseImpl#createNewDialogIncoming
	 * (org.mobicents.protocols.ss7.map.api.MAPApplicationContext,
	 * org.mobicents.protocols.ss7.tcap.api.tc.dialog.Dialog)
	 */
	protected MAPDialogImpl createNewDialogIncoming(MAPApplicationContext appCntx, Dialog tcapDialog) {
		return new MAPDialogSupplementaryImpl(appCntx, tcapDialog, this.mapProviderImpl, this, null, null);
	}

	public void addMAPServiceListener(MAPServiceSupplementaryListener mapServiceListener) {
		super.addMAPServiceListener(mapServiceListener);
	}

	public void removeMAPServiceListener(MAPServiceSupplementaryListener mapServiceListener) {
		super.removeMAPServiceListener(mapServiceListener);
	}

	public ServingCheckData isServingService(MAPApplicationContext dialogApplicationContext) {
		MAPApplicationContextName ctx = dialogApplicationContext.getApplicationContextName();
		int vers = dialogApplicationContext.getApplicationContextVersion().getVersion();

		switch (ctx) {
		case networkUnstructuredSsContext:
			if (vers == 2) {
				return new ServingCheckDataImpl(ServingCheckResult.AC_Serving);
			} else if (vers > 2) {
				long[] altOid = dialogApplicationContext.getOID();
				altOid[7] = 2;
				ApplicationContextName alt = TcapFactory.createApplicationContextName(altOid);
				return new ServingCheckDataImpl(ServingCheckResult.AC_VersionIncorrect, alt);
			} else {
				return new ServingCheckDataImpl(ServingCheckResult.AC_VersionIncorrect);
			}
			
		}

		return new ServingCheckDataImpl(ServingCheckResult.AC_NotServing);
	}

	public void processComponent(ComponentType compType, OperationCode oc, Parameter parameter, MAPDialog mapDialog, Long invokeId, Long linkedId)
			throws MAPParsingComponentException {

		MAPDialogSupplementaryImpl mapDialogSupplementaryImpl = (MAPDialogSupplementaryImpl) mapDialog;

		Long ocValue = oc.getLocalOperationCode();
		if (ocValue == null)
			new MAPParsingComponentException("", MAPParsingComponentExceptionReason.UnrecognizedOperation);

		long ocValueInt = ocValue;
		int ocValueInt2 = (int) ocValueInt;
		switch (ocValueInt2) {
		case MAPOperationCode.processUnstructuredSS_Request:
			if (compType == ComponentType.Invoke)
				this.processUnstructuredSSRequest(parameter, mapDialogSupplementaryImpl, invokeId);
			else
				this.processUnstructuredSSResponse(parameter, mapDialogSupplementaryImpl, invokeId);
			break;
		case MAPOperationCode.unstructuredSS_Request:
			if (compType == ComponentType.Invoke)
				this.unstructuredSSRequest(parameter, mapDialogSupplementaryImpl, invokeId);
			else
				this.unstructuredSSResponse(parameter, mapDialogSupplementaryImpl, invokeId);
			break;
		case MAPOperationCode.unstructuredSS_Notify:
			if (compType == ComponentType.Invoke)
				this.unstructuredSSNotifyRequest(parameter, mapDialogSupplementaryImpl, invokeId);
			else
				// error?
				break;
		default:
			new MAPParsingComponentException("", MAPParsingComponentExceptionReason.UnrecognizedOperation);
		}
	}

	private void unstructuredSSNotifyRequest(Parameter parameter, MAPDialogSupplementaryImpl mapDialogImpl, Long invokeId) throws MAPParsingComponentException {
		if (parameter == null)
			throw new MAPParsingComponentException("Error while decoding unstructuredSSNotifyIndication: Parameter is mandatory but not found",
					MAPParsingComponentExceptionReason.MistypedParameter);

		if (parameter.getTag() != Tag.SEQUENCE || parameter.getTagClass() != Tag.CLASS_UNIVERSAL || parameter.isPrimitive())
			throw new MAPParsingComponentException(
					"Error while decoding unstructuredSSNotifyIndication: Bad tag or tagClass or parameter is primitive, received tag=" + parameter.getTag(),
					MAPParsingComponentExceptionReason.MistypedParameter);

		byte[] buf = parameter.getData();
		AsnInputStream ais = new AsnInputStream(buf);

		UnstructuredSSNotifyRequestIndicationImpl ind = new UnstructuredSSNotifyRequestIndicationImpl();
		ind.decodeData(ais, buf.length);
		ind.setInvokeId(invokeId);
		ind.setMAPDialog(mapDialogImpl);

		for (MAPServiceListener serLis : this.serviceListeners) {
			try {
				((MAPServiceSupplementaryListener) serLis).onUnstructuredSSNotifyRequestIndication(ind);
			} catch (Exception e) {
				loger.error("Error processing unstructuredSSNotifyIndication: " + e.getMessage(), e);
			}
		}
	}
	
	private void unstructuredSSNotifyResponse(Parameter parameter, MAPDialogSupplementaryImpl mapDialogImpl, Long invokeId) throws MAPParsingComponentException {
		if (parameter == null)
			throw new MAPParsingComponentException("Error while decoding unstructuredSSNotifyIndication: Parameter is mandatory but not found",
					MAPParsingComponentExceptionReason.MistypedParameter);

		if (parameter.getTag() != Tag.SEQUENCE || parameter.getTagClass() != Tag.CLASS_UNIVERSAL || parameter.isPrimitive())
			throw new MAPParsingComponentException(
					"Error while decoding unstructuredSSNotifyIndication: Bad tag or tagClass or parameter is primitive, received tag=" + parameter.getTag(),
					MAPParsingComponentExceptionReason.MistypedParameter);

		byte[] buf = parameter.getData();
		AsnInputStream ais = new AsnInputStream(buf);

		UnstructuredSSNotifyRequestIndicationImpl ind = new UnstructuredSSNotifyRequestIndicationImpl();
		ind.decodeData(ais, buf.length);
		ind.setInvokeId(invokeId);
		ind.setMAPDialog(mapDialogImpl);

		for (MAPServiceListener serLis : this.serviceListeners) {
			try {
				((MAPServiceSupplementaryListener) serLis).onUnstructuredSSNotifyRequestIndication(ind);
			} catch (Exception e) {
				loger.error("Error processing unstructuredSSNotifyIndication: " + e.getMessage(), e);
			}
		}
	}

	private void unstructuredSSRequest(Parameter parameter, MAPDialogSupplementaryImpl mapDialogImpl, Long invokeId) throws MAPParsingComponentException {
		if (parameter == null)
			throw new MAPParsingComponentException("Error while decoding UnstructuredSSRequestIndication: Parameter is mandatory but not found",
					MAPParsingComponentExceptionReason.MistypedParameter);

		if (parameter.getTag() != Tag.SEQUENCE || parameter.getTagClass() != Tag.CLASS_UNIVERSAL || parameter.isPrimitive())
			throw new MAPParsingComponentException(
					"Error while decoding UnstructuredSSRequestIndication: Bad tag or tagClass or parameter is primitive, received tag=" + parameter.getTag(),
					MAPParsingComponentExceptionReason.MistypedParameter);

		byte[] buf = parameter.getData();
		AsnInputStream ais = new AsnInputStream(buf);

		UnstructuredSSRequestIndicationImpl ind = new UnstructuredSSRequestIndicationImpl();
		ind.decodeData(ais, buf.length);
		ind.setInvokeId(invokeId);
		ind.setMAPDialog(mapDialogImpl);

		for (MAPServiceListener serLis : this.serviceListeners) {
			try {
				((MAPServiceSupplementaryListener) serLis).onUnstructuredSSRequestIndication(ind);
			} catch (Exception e) {
				loger.error("Error processing UnstructuredSSRequestIndication: " + e.getMessage(), e);
			}
		}
	}

	private void unstructuredSSResponse(Parameter parameter, MAPDialogSupplementaryImpl mapDialogImpl, Long invokeId) throws MAPParsingComponentException {

		if (parameter == null)
			throw new MAPParsingComponentException("Error while decoding UnstructuredSSResponseIndication: Parameter is mandatory but not found",
					MAPParsingComponentExceptionReason.MistypedParameter);

		if (parameter.getTag() != Tag.SEQUENCE || parameter.getTagClass() != Tag.CLASS_UNIVERSAL || parameter.isPrimitive())
			throw new MAPParsingComponentException(
					"Error while decoding UnstructuredSSResponseIndication: Bad tag or tagClass or parameter is primitive, received tag=" + parameter.getTag(),
					MAPParsingComponentExceptionReason.MistypedParameter);

		byte[] buf = parameter.getData();
		AsnInputStream ais = new AsnInputStream(buf);

		UnstructuredSSResponseIndicationImpl ind = new UnstructuredSSResponseIndicationImpl();
		ind.decodeData(ais, buf.length);
		ind.setInvokeId(invokeId);
		ind.setMAPDialog(mapDialogImpl);

		for (MAPServiceListener serLis : this.serviceListeners) {
			try {
				((MAPServiceSupplementaryListener) serLis).onUnstructuredSSResponseIndication(ind);
			} catch (Exception e) {
				loger.error("Error processing UnstructuredSSResponseIndication: " + e.getMessage(), e);
			}
		}

	}

	private void processUnstructuredSSRequest(Parameter parameter, MAPDialogSupplementaryImpl mapDialogImpl, Long invokeId) throws MAPParsingComponentException {

		if (parameter == null)
			throw new MAPParsingComponentException("Error while decoding ProcessUnstructuredSSRequestIndication: Parameter is mandatory but not found",
					MAPParsingComponentExceptionReason.MistypedParameter);

		if (parameter.getTag() != Tag.SEQUENCE || parameter.getTagClass() != Tag.CLASS_UNIVERSAL || parameter.isPrimitive())
			throw new MAPParsingComponentException(
					"Error while decoding ProcessUnstructuredSSRequestIndication: Bad tag or tagClass or parameter is primitive, received tag="
							+ parameter.getTag(), MAPParsingComponentExceptionReason.MistypedParameter);

		byte[] buf = parameter.getData();
		AsnInputStream ais = new AsnInputStream(buf);

		ProcessUnstructuredSSRequestIndicationImpl ind = new ProcessUnstructuredSSRequestIndicationImpl();
		ind.decodeData(ais, buf.length);
		ind.setInvokeId(invokeId);
		ind.setMAPDialog(mapDialogImpl);

		for (MAPServiceListener serLis : this.serviceListeners) {
			try {
				((MAPServiceSupplementaryListener) serLis).onProcessUnstructuredSSRequestIndication(ind);
			} catch (Exception e) {
				loger.error("Error processing ProcessUnstructuredSSRequestIndication: " + e.getMessage(), e);
			}
		}

	}

	private void processUnstructuredSSResponse(Parameter parameter, MAPDialogSupplementaryImpl mapDialogImpl, Long invokeId)
			throws MAPParsingComponentException {

		if (parameter == null)
			throw new MAPParsingComponentException("Error while decoding ProcessUnstructuredSSResponseIndication: Parameter is mandatory but not found",
					MAPParsingComponentExceptionReason.MistypedParameter);

		if (parameter.getTag() != Tag.SEQUENCE || parameter.getTagClass() != Tag.CLASS_UNIVERSAL || parameter.isPrimitive())
			throw new MAPParsingComponentException(
					"Error while decoding ProcessUnstructuredSSResponseIndication: Bad tag or tagClass or parameter is primitive, received tag="
							+ parameter.getTag(), MAPParsingComponentExceptionReason.MistypedParameter);

		byte[] buf = parameter.getData();
		AsnInputStream ais = new AsnInputStream(buf);

		ProcessUnstructuredSSResponseIndicationImpl ind = new ProcessUnstructuredSSResponseIndicationImpl();
		ind.decodeData(ais, buf.length);
		ind.setInvokeId(invokeId);
		ind.setMAPDialog(mapDialogImpl);

		for (MAPServiceListener serLis : this.serviceListeners) {
			try {
				((MAPServiceSupplementaryListener) serLis).onProcessUnstructuredSSResponseIndication(ind);
			} catch (Exception e) {
				loger.error("Error processing ProcessUnstructuredSSResponseIndication: " + e.getMessage(), e);
			}
		}

	}
}