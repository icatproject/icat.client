class Session(object):
    
    def __init__(self, icat, sessionId):
        self.icat = icat
        self.sessionId = sessionId
        
    def getUserName(self):
        return self.icat._getUserName(self.sessionId)
    
    def getRemainingMinutes(self):
        return self.icat._getRemainingMinutes(self.sessionId)
    
    def logout(self):
        self.icat._logout(self.sessionId)
        
    def refresh(self):
        self.icat._refresh(self.sessionId)
        
    def search(self, query):
        return self.icat._search(self.sessionId, query)
    
    def delete(self, entities):
        self.icat._delete(self.sessionId, entities)
        
    def write(self, entities):
        return self.icat._write(self.sessionId, entities)
    
    def get(self, query, tid):
        return self.icat._get(self.sessionId, query, tid)