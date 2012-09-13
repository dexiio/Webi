package com.vonhof.webi.mongo;

import com.mongodb.BasicDBObject;
import com.mongodb.DBObject;
import com.vonhof.babelshark.BabelSharkInstance;
import com.vonhof.babelshark.SharkConverter;
import com.vonhof.babelshark.exception.MappingException;
import com.vonhof.babelshark.node.SharkNode;
import com.vonhof.babelshark.node.SharkType;
import java.util.Map;

/**
 *
 * @author Henrik Hofmeister <@vonhofdk>
 */
public class BasicDBObjectConverter implements SharkConverter<DBObject> {

    public <U> BasicDBObject deserialize(BabelSharkInstance bs, SharkNode node, SharkType<DBObject, U> type) throws MappingException {
        Map map = bs.read(node,Map.class);
        return new BasicDBObject(map);
    }

    public SharkNode serialize(BabelSharkInstance bs, DBObject value) throws MappingException {
        SharkNode out = bs.write(value.toMap());
        return out;
    }

}
