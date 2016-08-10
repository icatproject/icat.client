import requests
import json
from Session import Session

class IcatException(Exception):
    
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
        self.code = code
        self.message = message
        self.offset = offset
        
    def __str__(self):
        return self.code + ": " + self.message
    
    def getMessage(self):
        return self.message
    
    def getType(self):
        return self.code
    
    def getOffset(self):
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
        self.uri = uri + "/icat/"
        self.cert = cert
        
    def login(self, plugin, cmap):
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
    
    def _get(self, sessionId, query, tid):
        r = requests.get(self.uri + "entityManager", params={"sessionId":sessionId, "query" : query, "id" : tid}, verify=self.cert)
        self._check(r)
        return r.json()
    
    def getApiVersion(self):
        r = requests.get(self.uri + "version", verify=self.cert)
        self._check(r)
        return r.json()