package com.dotmarketing.portlets.workflows.ajax;

import java.io.IOException;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import javax.servlet.ServletException;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;

import org.apache.commons.beanutils.BeanUtils;
import org.codehaus.jackson.JsonGenerationException;
import org.codehaus.jackson.map.DeserializationConfig.Feature;
import org.codehaus.jackson.map.JsonMappingException;
import org.codehaus.jackson.map.ObjectMapper;

import com.dotmarketing.business.APILocator;
import com.dotmarketing.business.Role;
import com.dotmarketing.business.RoleAPI;
import com.dotmarketing.business.UserAPI;
import com.dotmarketing.cms.factories.PublicCompanyFactory;
import com.dotmarketing.exception.DotDataException;
import com.dotmarketing.portlets.workflows.model.WorkflowAction;
import com.dotmarketing.util.Logger;
import com.dotmarketing.util.UtilMethods;
import com.liferay.portal.language.LanguageException;
import com.liferay.portal.language.LanguageUtil;
import com.liferay.portal.model.User;

public class WfRoleStoreAjax extends WfBaseAction {

	public void action(HttpServletRequest request, HttpServletResponse response) throws ServletException, IOException {

		String searchName = request.getParameter("searchName");

		if(searchName ==null) searchName ="";
		String roleId = request.getParameter("roleId");
		RoleAPI rapi = APILocator.getRoleAPI();	
		UserAPI uapi = APILocator.getUserAPI();
		
		int start = 0 ;
		int count = 20;
		try{
			start = Integer.parseInt(request.getParameter("start"));
		}
		catch(Exception e){
			
		}
		try {
			count = Integer.parseInt(request.getParameter("count"));
		} catch (Exception e) {

		}
		
		try {
			Role cmsAnon = rapi.loadCMSAnonymousRole();
			
			// Get the default User's Role
			User defaultUser = uapi.getDefaultUser();
			Role defaultUserRole = null;
			if(defaultUser != null) {
				rapi.getUserRole(defaultUser);
			}

			String cmsAnonName =LanguageUtil.get(getUser(), "current-user");
			cmsAnon.setName(cmsAnonName);
			boolean addSystemUser = false;
			if(searchName.length() > 0 && cmsAnonName.startsWith(searchName)){
				addSystemUser = true;
			}

	        List<Role> roleList = new ArrayList<Role>();
	        if(UtilMethods.isSet(roleId)){
	        	try{
	        		Role r = rapi.loadRoleById(roleId);
	        		if(r!= null){
	        		    if(r.getId().equals(cmsAnon.getId()))
	        		        roleList.add(cmsAnon);
	        		    else
	        		        roleList.add(r);
	        			response.getWriter().write(rolesToJson(roleList));
	        			return;
	        		}	        		
	        	}
	        	catch(Exception e){
	        		
	        	}
	        	
	        }	        
	        
	        //ISSUE 1734:
	        //returning 1 extra if possible to allow filtering select to know there are more to pull
			while(roleList.size() < count+1) { 			
				List<Role> roles = rapi.findRolesByFilterLeftWildcard(searchName, start, count);
				if(roles.size() ==0){
					break;
				}
		        for(Role role : roles){
		        	if(role.isUser()){		        		
			        	try {		        		
			        		uapi.loadUserById(role.getRoleKey(), uapi.getSystemUser(), false);
						} catch (Exception e) {						
							//Logger.error(WfRoleStoreAjax.class,e.getMessage(),e);
							continue;
						}
		        	}
		        	if(role.getId().equals(cmsAnon.getId())) {
		        		role = cmsAnon;
		        		addSystemUser = false;
		        	}
		        	
		        	// Exclude System Roles
		        	if(role.isSystem() && ! role.isUser() && !role.getId().equals(cmsAnon.getId()) && !role.getId().equals(rapi.loadCMSAdminRole().getId())){
		        		continue;
		        	}
		        	
		            //Exclude default user
		            try {
		            	if(defaultUserRole != null && role.getId().equals(defaultUserRole.getId())) {
		            		continue;
			            }
		            } catch (Exception e) { }


		        	if(role.getName().equals(searchName)) {
		        		roleList.add(0,role);
		        	} else {
		        		roleList.add(role);		        		
		        	}		        		        
		        }
		        start = start + count;
			}
			if(addSystemUser){
				cmsAnon.setName(cmsAnonName);
				roleList.add(0,cmsAnon);
			}

			
			//x = x.replaceAll("identifier", "x");
            response.getWriter().write(rolesToJson(roleList));

		} catch (Exception e) {
			Logger.error(WfRoleStoreAjax.class,e.getMessage(),e);
		}
		
	}
	
	public void assignable(HttpServletRequest request, HttpServletResponse response) throws ServletException, IOException {
	
		String name = request.getParameter("name");

		try {
			String actionId = request.getParameter("actionId");
			WorkflowAction action = APILocator.getWorkflowAPI().findAction(actionId, getUser());
			Role role = APILocator.getRoleAPI().loadRoleById(action.getNextAssign());
	        List<Role> roleList = new ArrayList<Role>();
	        List<User> userList = new ArrayList<User>();
			if(!role.isUser()){
				if(action.isRoleHierarchyForAssign()){
			        userList = APILocator.getRoleAPI().findUsersForRole(role, true);
			        roleList.addAll(APILocator.getRoleAPI().findRoleHierarchy(role));	
				}
				else{
			        userList = APILocator.getRoleAPI().findUsersForRole(role, false);
			        roleList.add(role);
				}
			}
			else{
				userList.add(APILocator.getUserAPI().loadUserById(role.getRoleKey(), APILocator.getUserAPI().getSystemUser(), false));	
			
			}
			
			

	        

			for(User user :userList){
				Role r =APILocator.getRoleAPI().getUserRole(user);
				if(r !=null && UtilMethods.isSet(r.getId())){
					roleList.add(r);
				}
			}
			if(name != null){
				
				name = name.toLowerCase().replaceAll("\\*", "");
				if(UtilMethods.isSet(name)){
					List<Role> newRoleList = new ArrayList<Role>();
					for(Role r : roleList){
						if(r.getName().toLowerCase().startsWith(name)){
							newRoleList.add(r);
						}
					}
					roleList = newRoleList;
				}
				
			}
			
			
			
			
			
			
            response.getWriter().write(rolesToJson(roleList));
		} catch (Exception e) {
			Logger.error(WfRoleStoreAjax.class,e.getMessage(),e);
		}
		
		
	}

    private String rolesToJson ( List<Role> roles ) throws IOException, DotDataException, LanguageException {

        ObjectMapper mapper = new ObjectMapper();
        mapper.configure( Feature.FAIL_ON_UNKNOWN_PROPERTIES, false );
        Map<String, Object> m = new LinkedHashMap<String, Object>();

        List<Map<String, Object>> list = new ArrayList<Map<String, Object>>();
        Map<String, Object> map;

        //Role cmsAnon = APILocator.getRoleAPI().loadCMSAnonymousRole();
        for ( Role role : roles ) {
            map = new HashMap<String, Object>();

            map.put( "name", role.getName() + ((role.isUser()) ? " (" + LanguageUtil.get( PublicCompanyFactory.getDefaultCompany(), "User" ) + ")" : "") );
            map.put( "id", role.getId() );

            list.add( map );
        }

        m.put( "identifier", "id" );
        m.put( "label", "name" );
        m.put( "items", list );

        return mapper.defaultPrettyPrintingWriter().writeValueAsString( m );
    }

}