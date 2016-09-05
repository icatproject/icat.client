import requests
import json
from requests_toolbelt import MultipartEncoder

class IcatException(Exception):
    """
    Thrown by the code to indicate problems.
    """
    
    #   An input parameter appears to be incorrect
    BAD_PARAMETER = "BAD_PARAMETER"
    # Some internal error has occurred */
    INTERNAL = "INTERNAL"
    # This is normally an authorization problem */
    INSUFFICIENT_PRIVILEGES = "INSUFFICIENT_PRIVILEGES" 
    # The requested object does not exist */
    NO_SUCH_OBJECT_FOUND = "NO_SUCH_OBJECT_FOUND"
    # An object already exists with the same key fields */
    OBJECT_ALREADY_EXISTS = "OBJECT_ALREADY_EXISTS"
    # This is normally an authentication problem or the session has expired
    SESSION = "SESSION"
    # If the call is not appropriate for the system in the current state */
    VALIDATION = "VALIDATION"
    # If no implementation is provided by the server */
    NOT_IMPLEMENTED = "NOT_IMPLEMENTED"
    
    def __init__(self, code, message, offset=-1):
        """
        Not expected to be called by most users
        """
        self.code = code
        self.message = message
        self.offset = offset
        
    def __str__(self):
        return self.code + ": " + self.message
    
    def getMessage(self):
        """
        Return a human readable message
        """
        return self.message
    
    def getType(self):
        """
        Return the type of the exception as a string
        """
        return self.code
    
    def getOffset(self):
        """
        Return the offset or -1 if not applicable
        """
        return self.offset

class ICAT(object):
    
    def _check(self, r):
        if r.status_code / 100 != 2:
            json = r.json()
            if json.get("offset"):
                raise IcatException(json["code"], json["message"], json["offset"])
            else:
                raise IcatException(json["code"], json["message"])

    def __init__(self, uri, cert=None):
        """
        Create a RESTful ICAT instance connected to the server at the specified
        URI.
        
        A cert may be specified for self signed certificates - it must be 
        in DER format. If the certificate has a value of False then 
        certificate checking is disabled.
        """
        self.uri = uri + "/icat/"
        self.cert = cert
        
    def login(self, plugin, cmap):
        """
        Login and return a session
        
        The plugin argument is the mnemonic of the authenticator and cmap is a
        map of credential keys to credential values 
        """
        credentials = []
        for key in cmap:
            credential = {}
            credential[key] = cmap[key]
            credentials.append(credential)
     
        arg = {}
        arg["plugin"] = plugin
        arg["credentials"] = credentials
 
        r = requests.post(self.uri + "session", data={'json': json.dumps(arg)}, verify=self.cert)
        self._check(r)
        sessionId = r.json()["sessionId"]
        return Session(self, sessionId)
    
    def _getUserName(self, sessionId):
        r = requests.get(self.uri + "session/" + sessionId, verify=self.cert)
        self._check(r)
        return r.json()["userName"]
    
    def _getRemainingMinutes(self, sessionId):
        r = requests.get(self.uri + "session/" + sessionId, verify=self.cert)
        self._check(r)
        return r.json()["remainingMinutes"]
    
    def isLoggedIn(self, userName):
        """
        Return true if the specified user has at least one session else 
        false. 
        
        The userName passed in must match that returned by
        getUserName and so must include the authenticator mnemomic if 
        the authenticator was configured to include it - which should
        generally be the case.
        """ 
        r = requests.get(self.uri + "user/" + userName, verify=self.cert)   
        self._check(r)
        return r.json()["loggedIn"]
    
    def _logout(self, sessionId):
        r = requests.delete(self.uri + "session/" + sessionId, verify=self.cert)
        self._check(r)
       
    def _refresh(self, sessionId):
        r = requests.put(self.uri + "session/" + sessionId, verify=self.cert)
        self._check(r)
        
    def _search(self, sessionId, query):
        r = requests.get(self.uri + "entityManager", params={"sessionId":sessionId, "query" : query}, verify=self.cert)
        self._check(r)
        return r.json()  
    
    def _delete(self, sessionId, entities):
        r = requests.delete(self.uri + "entityManager", params={"sessionId":sessionId, "entities" : json.dumps(entities)}, verify=self.cert)
        self._check(r)
        
    def _write(self, sessionId, entities):
        r = requests.post(self.uri + "entityManager", data={"sessionId":sessionId, "entities" : json.dumps(entities)}, verify=self.cert)
        self._check(r)
        return r.json()
    
    def getVersion(self):
        """
        Returns the version of the ICAT server
        """
        r = requests.get(self.uri + "version", verify=self.cert)
        self._check(r)
        return r.json()
    
    def _clone(self, sessionId, name, idValue, keys):
        r = requests.post(self.uri + "cloner", data={"sessionId":sessionId, "name":name, "id":idValue, "keys":json.dumps(keys)}, verify=self.cert)
        self._check(r)
        return r.json()["id"]
    
    def _export(self, sessionId, query, attributes):
        parms = {"sessionId":sessionId}
        if query: parms["query"] = query
        if attributes: parms["attributes"] = attributes
        
        r = requests.get(self.uri + "port", params={"json":json.dumps(parms)}, verify=self.cert)
        self._check(r)
        return r.text
    
    def _import(self, sessionId, data, duplicate, attributes):
        
        parms = {"sessionId": sessionId}
        if duplicate: parms["duplicate"] = duplicate
        if attributes: parms["attributes"] = attributes
        
        fields = {}
        fields['json'] = json.dumps(parms)
        fields['file'] = ('filename', data, 'application/octet-stream')     
        m = MultipartEncoder(fields=fields)

        r = requests.post(self.uri + "port", data=m, headers={'Content-Type': m.content_type}, verify=self.cert)
        self._check(r)
        
class Session(object):
    
    def __init__(self, icat, sessionId):
        """
        Not expected to be called by most users. A Session object
        is normally generated by a login call to an ICAT object.
        """
        self.icat = icat
        self.sessionId = sessionId
        
    def getUserName(self):
        """
        Return the user name corresponding to the session.
        """
        return self.icat._getUserName(self.sessionId)
    
    def getRemainingMinutes(self):
        """
        Return the time remaining in the session in minutes
        """
        return self.icat._getRemainingMinutes(self.sessionId)
    
    def logout(self):
        """
        Logout of the session after which the session cannot be re-used
        """
        self.icat._logout(self.sessionId)
        
    def refresh(self):
        """
        Refresh the session by resetting the time remaining
        """
        self.icat._refresh(self.sessionId)
        
    def search(self, query):
        """
        Carry out an ICAT search and return the results
    
        The query takes the form of JPQL. For example the query
        
        SELECT f.id, f.name from Facility f
        
        returns a list of lists which in this case might be:
        
        [[17, "abcd"], [18, "efgh"]]
        
        If only one attribute is requested then it returns simply a list of values so that
        
        "SELECT f.name from Facility f" would return ["abcd", "efgh"]
        
        Functions such as COUNT are not treated as special cases so the query 
        "SELECT COUNT(f) FROM Facility f" would in this case return [2] which is a list
        of length 1 
        
        The old query syntax is also supported but is not recommended as it is 
        better adapted to SOAP calls.
        """
        return self.icat._search(self.sessionId, query)
    
    def delete(self, entities):
        """
        Delete ICAT entities from nested lists and dicts.
        
        If there are multiple top level entities to be deleted then the argument
        passed in must be a list. Each top level entity is represented by a dict
        with a key of the entity name and a value which is a dict where the keys
        are the attribute names and the values are the entity field values.
        The only permitted key is the id - which is also required.
        
        For example to delete an Investigation:
        
        entity = {"Investigation" : {"id" : dsid}}
        self.session.delete(entity)
        
        Remember that all to-many relationships will be followed so, in this case,
        all the Datasets of the Investigation will be deleted recursively. DELETE 
        permission is required for every entity in the tree.
        """
        self.icat._delete(self.sessionId, entities)
   
    def write(self, entities):
        """
        Write (create or update) ICAT entities from nested lists and dicts.
         
        If there are multiple top level entities to be handled then the argument
        passed in must be a list. Each top level entity is represented by a dict with a 
        key of the entity name and a value which is a dict where the keys are the 
        attribute names and the values are the entity field values.
         
        A list of the ids of the top level entities created is returned.
        
        A simple example which will create a single Facility and return its id is:
        
        facility = {}
        facility["name"] = "Test Facility"
        entity = {"Facility":facility}
        fid = session.write(entity)[0]
        
        or in one line:
        
        fid = session.write({"Facility":{"name":Test Facility}})
        
        If the attribute is a many to one relationship then the value is a dict and if it 
        is a one to many relationship then the value is a list of dicts.
        
        For example to create a dataset referencing the already existing Investigation 
        with id of invid and DatasetType with id of dstid and creating two new datafiles:
        
        dataset = {"name" : "ds1", "investigation" : { "id" : invid}, "type": {"id":dstid}}
        dataset["datafiles"] = [{"name" : "df1"}, {"name":"df2"}]
        entity = {"Dataset" : dataset}
        dsid = self.session.write(entity)[0]
        
        In all cases many to one relationships must already exist and so must 
        be specified by their id value while one to many relationships cannot exist
        prior to the call and so the id value is omitted.
        
        For updating pass in the id of top level entity and the attributes you specify 
        will be updated. In the case of a one to many relationship any new entities 
        will be appended. So to add a Datafile to a Dataset one could write and at
        the same time updating the description field:
        
        dataset["id"] = dsid
        dataset["datafiles"] = [{"name" : "df3"}]
        dataset["description"] = "Indescribable"
        entity = {"Dataset" : dataset}
        self.session.write(entity)
        
        This will return an empty list because no new Dataset has been created and so 
        this is regarded as an update. The similar operation:
        
        datafile = {"name" : "df3", dataset {"id" : dsid}}
        entity = {"Datafile" : datafile}
        dfid = self.session.write(entity)[0]
        
        also results in a Datafile being added to the Dataset but in this case it is
        treated as a creation and the id of the Datafile is returned. If it is wished to 
        update the dataset description at the same time then one could write:
        
        entities = []
        datafile = {"name" : "df3", dataset {"id" : dsid}}
        entities.append({"Datafile" : datafile})
        dataset["id"] = dsid
        dataset["description"] = "Indescribable"
        entities.append({"Dataset" : dataset})
        dfid = self.session.write(entity)[0]
        
        where this time as list (an existing Dataset to be updated) and a Datafile to be created
        are passed in.
        """  
        return self.icat._write(self.sessionId, entities)
    
    def cloneEntity(self, name, idValue, keys):
        """  
        Clone an entity and return the id of the clone
     
        name is the name of the type of entity and idValue is the id value of the entity to be cloned
     
        keys is a dict with mappings from field names to values to be different in the clone

        the id of the clone is returned
        """
        return self.icat._clone(self.sessionId, name, idValue, keys)
        
    ALL = "ALL"
    USER = "USER"
 
    def exportMetaData(self, query=None, attributes=None):
        return self.icat._export(self.sessionId, query, attributes)
    
    CHECK = "CHECK"
    IGNORE = "IGNORE"
    OVERWRITE = "OVERWRITE"
    THROW = "THROW"
    
    def importMetaData(self, data, duplicate=None, attributes=None):
        self.icat._import(self.sessionId, data, duplicate, attributes)
    
