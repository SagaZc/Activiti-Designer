/**
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package org.activiti.designer.util.eclipse;

import java.util.Collection;
import java.util.List;

import org.activiti.bpmn.model.Activity;
import org.activiti.bpmn.model.Artifact;
import org.activiti.bpmn.model.Association;
import org.activiti.bpmn.model.BaseElement;
import org.activiti.bpmn.model.BoundaryEvent;
import org.activiti.bpmn.model.FlowElement;
import org.activiti.bpmn.model.FlowElementsContainer;
import org.activiti.bpmn.model.Lane;
import org.activiti.bpmn.model.MessageFlow;
import org.activiti.bpmn.model.Pool;
import org.activiti.bpmn.model.Process;
import org.activiti.bpmn.model.SubProcess;
import org.activiti.bpmn.model.TextAnnotation;
import org.activiti.designer.util.editor.BpmnMemoryModel;
import org.activiti.designer.util.editor.KickstartProcessMemoryModel;
import org.activiti.designer.util.editor.ModelHandler;
import org.activiti.workflow.simple.definition.StepDefinition;
import org.apache.commons.lang.ArrayUtils;
import org.eclipse.core.resources.IProject;
import org.eclipse.core.resources.IProjectDescription;
import org.eclipse.core.resources.IResource;
import org.eclipse.core.resources.ResourcesPlugin;
import org.eclipse.core.runtime.CoreException;
import org.eclipse.core.runtime.IPath;
import org.eclipse.core.runtime.NullProgressMonitor;
import org.eclipse.emf.common.util.EList;
import org.eclipse.emf.common.util.URI;
import org.eclipse.emf.ecore.EObject;
import org.eclipse.emf.ecore.resource.Resource;
import org.eclipse.emf.ecore.util.EcoreUtil;
import org.eclipse.emf.transaction.RecordingCommand;
import org.eclipse.emf.transaction.TransactionalEditingDomain;
import org.eclipse.gef.ui.actions.ActionRegistry;
import org.eclipse.graphiti.features.context.ICustomContext;
import org.eclipse.graphiti.mm.GraphicsAlgorithmContainer;
import org.eclipse.graphiti.mm.algorithms.AlgorithmsFactory;
import org.eclipse.graphiti.mm.algorithms.Ellipse;
import org.eclipse.graphiti.mm.algorithms.GraphicsAlgorithm;
import org.eclipse.graphiti.mm.pictograms.Diagram;
import org.eclipse.graphiti.mm.pictograms.PictogramElement;
import org.eclipse.graphiti.services.IGaService;
import org.eclipse.graphiti.ui.editor.DiagramEditor;
import org.eclipse.jdt.core.IClasspathEntry;
import org.eclipse.jdt.core.IJavaProject;
import org.eclipse.jdt.core.JavaCore;
import org.eclipse.jface.action.IAction;
import org.eclipse.ui.IWorkbenchPart;
import org.eclipse.ui.PlatformUI;

public class ActivitiUiUtil {

  private static final String ID_PATTERN = "%s%s";

  public static void runModelChange(final Runnable runnable, final TransactionalEditingDomain editingDomain, final String label) {

    editingDomain.getCommandStack().execute(new RecordingCommand(editingDomain, label) {

      protected void doExecute() {
        runnable.run();
      }
    });
  }

  @SuppressWarnings("rawtypes")
  public static boolean contextPertainsToBusinessObject(final ICustomContext context, final Class businessClass) {
    boolean result = false;
    EList<EObject> businessObjects = context.getInnerPictogramElement().getLink().getBusinessObjects();
    for (final EObject eobj : businessObjects) {
      if (businessClass.equals(eobj.getClass())) {
        result = true;
        break;
      }
    }
    return result;
  }

  @SuppressWarnings("rawtypes")
  public static Object getBusinessObjectFromContext(final ICustomContext context, final Class businessClass) {
    Object result = null;
    EList<EObject> businessObjects = context.getInnerPictogramElement().getLink().getBusinessObjects();
    for (final EObject eobj : businessObjects) {
      if (businessClass.equals(eobj.getClass())) {
        result = eobj;
        break;
      }
    }
    return result;
  }

  public static Ellipse createInvisibleEllipse(GraphicsAlgorithmContainer gaContainer, IGaService gaService) {
    Ellipse ret = AlgorithmsFactory.eINSTANCE.createEllipse();
    ret.setX(0);
    ret.setY(0);
    ret.setWidth(0);
    ret.setHeight(0);
    ret.setFilled(false);
    ret.setLineVisible(false);
    if (gaContainer instanceof PictogramElement) {
      PictogramElement pe = (PictogramElement) gaContainer;
      pe.setGraphicsAlgorithm(ret);
    } else if (gaContainer instanceof GraphicsAlgorithm) {
      GraphicsAlgorithm parentGa = (GraphicsAlgorithm) gaContainer;
      parentGa.getGraphicsAlgorithmChildren().add(ret);
    }
    return ret;
  }

  public static void doProjectReferenceChange(IProject currentProject, IJavaProject containerProject, String className) throws CoreException {

    if (currentProject.equals(containerProject.getProject())) {
      return;
    }

    IProjectDescription desc = currentProject.getDescription();
    IProject[] refs = desc.getReferencedProjects();
    IProject[] newRefs = new IProject[refs.length + 1];
    System.arraycopy(refs, 0, newRefs, 0, refs.length);
    newRefs[refs.length] = containerProject.getProject();
    desc.setReferencedProjects(newRefs);
    currentProject.setDescription(desc, new NullProgressMonitor());

    IPath dependsOnPath = containerProject.getProject().getFullPath();

    IJavaProject javaProject = JavaCore.create(currentProject);
    IClasspathEntry prjEntry = JavaCore.newProjectEntry(dependsOnPath, true);

    boolean dependsOnPresent = false;
    for (IClasspathEntry cpEntry : javaProject.getRawClasspath()) {
      if (cpEntry.equals(prjEntry)) {
        dependsOnPresent = true;
      }
    }

    if (!dependsOnPresent) {
      IClasspathEntry[] entryList = new IClasspathEntry[1];
      entryList[0] = prjEntry;
      IClasspathEntry[] newEntries = (IClasspathEntry[]) ArrayUtils.addAll(javaProject.getRawClasspath(), entryList);
      javaProject.setRawClasspath(newEntries, null);
    }

  }

  public static IProject getProjectFromDiagram(Diagram diagram) {

    IProject currentProject = null;
    Resource resource = diagram.eResource();

    URI uri = resource.getURI();
    URI uriTrimmed = uri.trimFragment();

    if (uriTrimmed.isPlatformResource()) {

      String platformString = uriTrimmed.toPlatformString(true);
      IResource fileResource = ResourcesPlugin.getWorkspace().getRoot().findMember(platformString);

      if (fileResource != null) {
        currentProject = fileResource.getProject();
      }
    } else {
    	IResource fileResource = ResourcesPlugin.getWorkspace().getRoot().findMember(uriTrimmed.toString());
    	
    	if (fileResource != null) {
        currentProject = fileResource.getProject();
      }
    }
    return currentProject;
  }

  /**
   * Gets the {@link ActionRegistry} for the currently active editor.
   * 
   * @return the ActionRegistry or null
   */
  public static final ActionRegistry getActionRegistry() {
    IWorkbenchPart part = PlatformUI.getWorkbench().getActiveWorkbenchWindow().getActivePage().getActivePart();
    if (part instanceof DiagramEditor) {
      DiagramEditor editor = (DiagramEditor) part;
      return (ActionRegistry) editor.getAdapter(ActionRegistry.class);
    }
    return null;
  }

  /**
   * Runs the action with the provided id for the currently active editor, if
   * there is one.
   * 
   * @param actionId
   *          the id of the action to run
   */
  public static final void runAction(final String actionId) {
    final ActionRegistry registry = getActionRegistry();
    if (registry != null) {
      final IAction action = registry.getAction(actionId);
      if (action != null) {
        action.run();
      }
    }
  }

  public static final String getNextId(final Class<? extends BaseElement> featureClass, final String featureIdKey, final Diagram diagram) {
    BpmnMemoryModel model = ModelHandler.getModel(EcoreUtil.getURI(diagram));
    int determinedId = 0;
    
    if (featureClass.equals(Pool.class)) {
      determinedId = loopThroughPools(featureClass, determinedId, model.getBpmnModel().getPools(), featureIdKey);
    } else {
    
      for (Process process : model.getBpmnModel().getProcesses()) {
          
        if (featureClass.equals(Lane.class)) {
          determinedId = loopThroughLanes(featureClass, determinedId, process.getLanes(), featureIdKey);
        } else if (featureClass.equals(TextAnnotation.class) || featureClass.equals(Association.class)) {
          determinedId = loopThroughArtifacts(featureClass, determinedId, process, featureIdKey);
          
        } else if (featureClass.equals(MessageFlow.class)) {
          determinedId = loopThroughMessageFlows(determinedId, model.getBpmnModel().getMessageFlows().values(), featureIdKey);
        } else {
          determinedId = loopThroughElements(featureClass, determinedId, process.getFlowElements(), featureIdKey);
        }
      }
    }
    determinedId++;
    return String.format(ID_PATTERN, featureIdKey, determinedId);
  }
  
  public static int loopThroughPools(final Class<? extends BaseElement> featureClass, int determinedId, 
      List<Pool> poolList, final String featureIdKey) {
    
    for (Pool pool : poolList) {
      String contentObjectId = pool.getId().replace(featureIdKey, "");
      determinedId = getId(contentObjectId, determinedId);
    }
    return determinedId;
  }
  
  public static int loopThroughLanes(final Class<? extends BaseElement> featureClass, int determinedId, 
      List<Lane> laneList, final String featureIdKey) {
    
    for (Lane lane : laneList) {
      String contentObjectId = lane.getId().replace(featureIdKey, "");
      determinedId = getId(contentObjectId, determinedId);
    }
    return determinedId;
  }
  
  public static int loopThroughArtifacts(final Class<? extends BaseElement> featureClass, int determinedId, 
      FlowElementsContainer container, final String featureIdKey) {
    
    for (Artifact artifact : container.getArtifacts()) {
      String contentObjectId = artifact.getId().replace(featureIdKey, "");
      determinedId = getId(contentObjectId, determinedId);
    }
    
    for (FlowElement element : container.getFlowElements()) {
      if (element instanceof SubProcess) {
        determinedId = loopThroughArtifacts(featureClass, determinedId, (SubProcess) element, featureIdKey);
      }
    }
    
    return determinedId;
  }
  
  public static int loopThroughMessageFlows(int determinedId, Collection<MessageFlow> messageFlowList, 
      final String featureIdKey) {
    
    for (MessageFlow messageFlow : messageFlowList) {
      String contentObjectId = messageFlow.getId().replace(featureIdKey, "");
      determinedId = getId(contentObjectId, determinedId);
    }
    return determinedId;
  }
  
  public static int loopThroughElements(final Class<? extends BaseElement> featureClass, int determinedId, 
  		Collection<FlowElement> elementList, final String featureIdKey) {
  	
  	for (FlowElement element : elementList) {
      
      if (element instanceof SubProcess) {
      	determinedId = loopThroughElements(featureClass, determinedId, ((SubProcess) element).getFlowElements(), featureIdKey);
      }
      
      if (featureClass == BoundaryEvent.class && element instanceof Activity) {
      	Activity activity = (Activity) element;
      	for (BoundaryEvent boundaryEvent : activity.getBoundaryEvents()) {
      	  if (boundaryEvent.getId() != null) {
        		String contentObjectId = boundaryEvent.getId().replace(featureIdKey, "");
            determinedId = getId(contentObjectId, determinedId);
      	  }
        }
      }
      
      if (element.getClass() == featureClass) {
        String contentObjectId = element.getId().replace(featureIdKey, "");
        determinedId = getId(contentObjectId, determinedId);
      }
  	}
  	return determinedId;
  }
  
  private static int getId(String contentObjectId, int determinedId) {
    int newdId = determinedId;
    boolean isNumber = true;
    if (contentObjectId != null && contentObjectId.length() > 0) {
      
      for (int i = 0; i < contentObjectId.length(); i++) {
        if (Character.isDigit(contentObjectId.charAt(i)) == false) {
          isNumber = false;
        }
      }
      if (isNumber == true) {
        Integer intNumber = Integer.valueOf(contentObjectId);
        if (intNumber > newdId) {
          newdId = intNumber;
        }
      }
    }
    return newdId;
  }
  
  public static final String getNextStepId(final Class<? extends StepDefinition> featureClass, final String featureIdKey, final Diagram diagram) {
    KickstartProcessMemoryModel model = ModelHandler.getKickstartProcessModel(EcoreUtil.getURI(diagram));
    int determinedId = 0;
    
    for (StepDefinition step : model.getWorkflowDefinition().getSteps()) {
      if (step.getClass() == featureClass) {
        String contentObjectId = step.getId().replace(featureIdKey, "");
        determinedId = getId(contentObjectId, determinedId);
      }
    }
    determinedId++;
    return String.format(ID_PATTERN, featureIdKey, determinedId);
  }

}
