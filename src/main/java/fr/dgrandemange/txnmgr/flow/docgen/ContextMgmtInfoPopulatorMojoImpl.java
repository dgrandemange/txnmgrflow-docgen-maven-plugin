package fr.dgrandemange.txnmgr.flow.docgen;

import java.lang.annotation.Annotation;
import java.lang.reflect.Array;
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Map.Entry;

import org.apache.maven.plugin.logging.Log;

import fr.dgrandemange.ctxmgmt.annotation.UpdateContextRule;
import fr.dgrandemange.txnmgrworkflow.model.ParticipantInfo;
import fr.dgrandemange.txnmgrworkflow.model.SubFlowInfo;
import fr.dgrandemange.txnmgrworkflow.service.support.ContextMgmtInfoPopulatorAbstractImpl;

/**
 * A context management info populator using reflection to discover annotations
 * on participant classes<br>
 * 
 * @author dgrandemange
 * 
 */
public class ContextMgmtInfoPopulatorMojoImpl extends
		ContextMgmtInfoPopulatorAbstractImpl {

	private ClassLoader classLoader;

	private Log log;

	public ContextMgmtInfoPopulatorMojoImpl(ClassLoader classLoader, Log log) {
		this.classLoader = classLoader;
		this.log = log;
	}

	@Override
	public void processParticipantAnnotations(
			Map<String, List<ParticipantInfo>> jPosTxnMgrGroups) {
		for (Entry<String, List<ParticipantInfo>> entry : jPosTxnMgrGroups
				.entrySet()) {
			for (ParticipantInfo participantInfo : entry.getValue()) {
				if (participantInfo instanceof SubFlowInfo) {
					continue;
				}
				Map<String, String[]> updCtxAttrByTransId = new HashMap<String, String[]>();
				participantInfo.setUpdCtxAttrByTransId(updCtxAttrByTransId);
				try {
					@SuppressWarnings("rawtypes")
					Class pClazz;
					if (classLoader != null) {
						pClazz = Class.forName(participantInfo.getClazz(),
								true, classLoader);
					} else {
						pClazz = Class.forName(participantInfo.getClazz());
					}
					Annotation[] annotations = pClazz.getAnnotations();
					for (Annotation annotation : annotations) {

						Class<? extends Annotation> annotationType = annotation
								.annotationType();
						if ("UpdateContextRules".equals(
								annotationType.getSimpleName())) {
							try {
								Method method_value = annotationType.getMethod(
										"value", new Class[] {});
								Object invoked_value = method_value.invoke(
										annotation, new Object[] {});

								if (invoked_value.getClass().isArray()) {
									int length = Array.getLength(invoked_value);
									for (int i = 0; i < length; i++) {
										processUpdateContextRuleAnnotation(
												updCtxAttrByTransId,
												Array.get(invoked_value, i));
									}
								} else {
									processUpdateContextRuleAnnotation(
											updCtxAttrByTransId, invoked_value);
								}
							} catch (Exception e) {
								log.warn(e.getMessage());
							}
						}

					}
				} catch (ClassNotFoundException e) {
					// Safe to ignore : class has not been found in the
					// classpath but we don't bother
				}
			}
		}
	}

	protected void processUpdateContextRuleAnnotation(
			Map<String, String[]> updCtxAttrByTransId, Object invoked_value)
			throws NoSuchMethodException, IllegalAccessException,
			InvocationTargetException {

		String id = null;
		String[] attrNames = null;

		Class<? extends Object> class1 = invoked_value.getClass();

		Method method_id = class1.getMethod("id", new Class[] {});
		Object invoked_id = method_id.invoke(invoked_value, new Object[] {});
		id = (String) invoked_id;

		Method method_attrNames = class1.getMethod("attrNames", new Class[] {});
		Object invoked_attrNames = method_attrNames.invoke(invoked_value,
				new Object[] {});
		if (invoked_attrNames.getClass().isArray()) {
			attrNames = (String[]) invoked_attrNames;
		} else {
			attrNames = new String[] { (String) invoked_attrNames };
		}

		if ((attrNames != null) && (attrNames.length > 0)) {
			if (id == null) {
				updCtxAttrByTransId
						.put(UpdateContextRule.DEFAULT_ID, attrNames);
			} else {
				updCtxAttrByTransId.put(id, attrNames);
			}
		}
	}

}