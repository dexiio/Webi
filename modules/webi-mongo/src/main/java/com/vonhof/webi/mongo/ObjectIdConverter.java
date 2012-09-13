package com.vonhof.webi.mongo;

import com.vonhof.babelshark.BabelSharkInstance;
import com.vonhof.babelshark.SharkConverter;
import com.vonhof.babelshark.exception.MappingException;
import com.vonhof.babelshark.node.SharkNode;
import com.vonhof.babelshark.node.SharkType;
import com.vonhof.babelshark.node.ValueNode;
import org.bson.types.ObjectId;

/**
 *
 * @author Henrik Hofmeister <@vonhofdk>
 */
public class ObjectIdConverter implements SharkConverter<ObjectId> {

    public <U> ObjectId deserialize(BabelSharkInstance bs, SharkNode node, SharkType<ObjectId, U> type) throws MappingException {
        if (!node.is(SharkNode.NodeType.VALUE))
            throw new MappingException("ObjectId must be of nodetype value");
        ValueNode<String> value = (ValueNode<String>) node;
        return new ObjectId(value.getValue());
    }

    public SharkNode serialize(BabelSharkInstance bs, ObjectId value) throws MappingException {
        return new ValueNode<String>(value.toString());
    }

}
