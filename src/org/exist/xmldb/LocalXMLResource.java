package org.exist.xmldb;

import java.io.File;
import java.io.IOException;
import java.io.StringWriter;
import java.util.Date;
import java.util.Properties;

import javax.xml.transform.TransformerException;

import org.exist.EXistException;
import org.exist.dom.DocumentImpl;
import org.exist.dom.NodeProxy;
import org.exist.dom.XMLUtil;
import org.exist.security.Permission;
import org.exist.security.User;
import org.exist.storage.BrokerPool;
import org.exist.storage.DBBroker;
import org.exist.storage.serializers.Serializer;
import org.exist.util.Lock;
import org.exist.util.LockException;
import org.exist.util.serializer.DOMSerializer;
import org.exist.util.serializer.DOMSerializerPool;
import org.exist.util.serializer.DOMStreamer;
import org.exist.util.serializer.DOMStreamerPool;
import org.exist.util.serializer.SAXSerializer;
import org.exist.xquery.XPathException;
import org.exist.xquery.value.AtomicValue;
import org.exist.xquery.value.NodeValue;
import org.w3c.dom.Node;
import org.xml.sax.ContentHandler;
import org.xml.sax.SAXException;
import org.xml.sax.SAXNotRecognizedException;
import org.xml.sax.SAXNotSupportedException;
import org.xmldb.api.base.Collection;
import org.xmldb.api.base.ErrorCodes;
import org.xmldb.api.base.XMLDBException;
import org.xmldb.api.modules.XMLResource;

/**
 * Local implementation of XMLResource.
 */
public class LocalXMLResource extends AbstractEXistResource implements XMLResource {

	//protected DocumentImpl document = null;
	protected NodeProxy proxy = null;
	protected long id = -1;
	
	protected Properties outputProperties = null;

	// those are the different types of content this resource
	// may have to deal with
	protected String content = null;
	protected File file = null;
	protected Node root = null;
	protected AtomicValue value = null;

	public LocalXMLResource(User user, BrokerPool pool, LocalCollection parent,
			String did, long id) throws XMLDBException {
		super(user, pool, parent, did);
		this.id = id;
	}

	public LocalXMLResource(User user, BrokerPool pool, LocalCollection parent,
			NodeProxy p) throws XMLDBException {
		this(user, pool, parent, p.doc.getFileName(), p.gid);
		this.proxy = p;
	}

	public Object getContent() throws XMLDBException {
		if (content != null)
			return content;

		// Case 1: content is an external DOM node
		else if (root != null && !(root instanceof NodeValue)) {
			DOMSerializer serializer = DOMSerializerPool.getInstance()
					.borrowDOMSerializer();
			try {
				StringWriter writer = new StringWriter();
				serializer.setOutputProperties(getProperties());
				serializer.setWriter(writer);
				serializer.serialize(root);
				content = writer.toString();
			} catch (TransformerException e) {
				throw new XMLDBException(ErrorCodes.INVALID_RESOURCE, e
						.getMessage(), e);
			} finally {
				DOMSerializerPool.getInstance().returnDOMSerializer(serializer);
			}
			return content;

			// Case 2: content is an atomic value
		} else if (value != null) {
			try {
				return value.getStringValue();
			} catch (XPathException e) {
				throw new XMLDBException(ErrorCodes.INVALID_RESOURCE, e
						.getMessage(), e);
			}

			// Case 3: content is a file
		} else if (file != null) {
			try {
				content = XMLUtil.readFile(file);
				return content;
			} catch (IOException e) {
				throw new XMLDBException(ErrorCodes.VENDOR_ERROR,
						"error while reading resource contents", e);
			}

			// Case 4: content is a document or internal node
		} else {
		    DocumentImpl document = null;
			DBBroker broker = null;
			try {
				broker = pool.get(user);
				Serializer serializer = broker.getSerializer();
				serializer.setUser(user);
				serializer.setProperties(getProperties());
				if (root != null)
					content = serializer.serialize((NodeValue) root);
				else {
				    document = openDocument(broker, Lock.READ_LOCK);
					if (!document.getPermissions().validate(user,
							Permission.READ))
						throw new XMLDBException(ErrorCodes.PERMISSION_DENIED,
								"permission denied to read resource");
					if (id < 0)
						content = serializer.serialize(document);
					else {
						if (proxy == null)
							proxy = new NodeProxy(document, id);
						content = serializer.serialize(proxy);
					}
				}
				return content;
			} catch (SAXException saxe) {
				saxe.printStackTrace();
				throw new XMLDBException(ErrorCodes.VENDOR_ERROR, saxe
						.getMessage(), saxe);
			} catch (EXistException e) {
				throw new XMLDBException(ErrorCodes.VENDOR_ERROR, e
						.getMessage(), e);
			} catch (Exception e) {
				e.printStackTrace();
				throw new XMLDBException(ErrorCodes.VENDOR_ERROR, e
						.getMessage(), e);
			} finally {
			    closeDocument(document, Lock.READ_LOCK);
				pool.release(broker);
			}
		}
	}

	public Node getContentAsDOM() throws XMLDBException {
		if (root != null)
			return root;
		else if (value != null) {
			throw new XMLDBException(ErrorCodes.VENDOR_ERROR,
					"cannot return an atomic value as DOM node");
		} else {
		    DocumentImpl document = null;
			DBBroker broker = null;
			try {
				broker = pool.get(user);
				document = getDocument(broker, true);
				if (!document.getPermissions().validate(user, Permission.READ))
					throw new XMLDBException(ErrorCodes.PERMISSION_DENIED,
							"permission denied to read resource");
				if (id < 0)
					return document.getDocumentElement();
				else if (proxy != null)
					return document.getNode(proxy);
				else
					return document.getNode(id);
			} catch (EXistException e) {
				throw new XMLDBException(ErrorCodes.VENDOR_ERROR, e
						.getMessage(), e);
			} finally {
			    parent.getCollection().releaseDocument(document);
				pool.release(broker);
			}
		}
	}

	public void getContentAsSAX(ContentHandler handler) throws XMLDBException {
		DBBroker broker = null;
		// case 1: content is an external DOM node
		if (root != null && !(root instanceof NodeValue)) {
			try {
				String option = parent.properties.getProperty(
						Serializer.GENERATE_DOC_EVENTS, "false");
				DOMStreamer streamer = DOMStreamerPool.getInstance()
						.borrowDOMStreamer();
				streamer.setContentHandler(handler);
				streamer.serialize(root, option.equalsIgnoreCase("true"));
				DOMStreamerPool.getInstance().returnDOMStreamer(streamer);
			} catch (Exception e) {
				throw new XMLDBException(ErrorCodes.INVALID_RESOURCE, e
						.getMessage(), e);
			}
			
		// case 2: content is an atomic value
		} else if (value != null) {
			try {
				broker = pool.get(user);
				value.toSAX(broker, handler);
			} catch (EXistException e) {
				throw new XMLDBException(ErrorCodes.VENDOR_ERROR, e
						.getMessage(), e);
			} catch (SAXException e) {
				throw new XMLDBException(ErrorCodes.VENDOR_ERROR, e
						.getMessage(), e);
			} finally {
				pool.release(broker);
			}
			
		// case 3: content is an internal node or a document
		} else {
			try {
				broker = pool.get(user);
				Serializer serializer = broker.getSerializer();
				serializer.setUser(user);
				serializer.setProperties(getProperties());
				serializer.setContentHandler(handler);

				if (root != null)
					serializer.toSAX((NodeValue) root);
				else {
					DocumentImpl document = null;
					try {
						document = openDocument(broker, Lock.READ_LOCK);
						if (!document.getPermissions().validate(user,
								Permission.READ))
							throw new XMLDBException(ErrorCodes.PERMISSION_DENIED,
							"permission denied to read resource");
						String xml;
						if (id < 0)
							serializer.toSAX(document);
						else {
							if (proxy == null)
								proxy = new NodeProxy(document, id);
							
							serializer.toSAX(proxy);
						}
					} finally {
					    closeDocument(document, Lock.READ_LOCK);
						pool.release(broker);
					}
				}
			} catch (EXistException e) {
				throw new XMLDBException(ErrorCodes.VENDOR_ERROR, e
						.getMessage(), e);
			} catch (SAXException e) {
				throw new XMLDBException(ErrorCodes.VENDOR_ERROR, e
						.getMessage(), e);
			}
		}
	}

	public String getDocumentId() throws XMLDBException {
		return docId;
	}

	public String getId() throws XMLDBException {
		return id < 2 ? docId : Long.toString(id);
	}

	public Collection getParentCollection() throws XMLDBException {
		if (parent == null)
			throw new XMLDBException(ErrorCodes.VENDOR_ERROR,
					"collection parent is null");
		return parent;
	}

	public String getResourceType() throws XMLDBException {
		return "XMLResource";
	}

	public Date getCreationTime() throws XMLDBException {
		DBBroker broker = null;
		try {
			broker = pool.get(user);
			DocumentImpl document = getDocument(broker, false);
			if (!document.getPermissions().validate(user, Permission.READ))
				throw new XMLDBException(ErrorCodes.PERMISSION_DENIED,
						"permission denied to read resource");
			return new Date(document.getCreated());
		} catch (EXistException e) {
			throw new XMLDBException(ErrorCodes.UNKNOWN_ERROR, e.getMessage(),
					e);
		} finally {
			pool.release(broker);
		}
	}

	public Date getLastModificationTime() throws XMLDBException {
		DBBroker broker = null;
		try {
			broker = pool.get(user);
			DocumentImpl document = getDocument(broker, false);
			if (!document.getPermissions().validate(user, Permission.READ))
				throw new XMLDBException(ErrorCodes.PERMISSION_DENIED,
						"permission denied to read resource");
			return new Date(document.getLastModified());
		} catch (EXistException e) {
			throw new XMLDBException(ErrorCodes.UNKNOWN_ERROR, e.getMessage(),
					e);
		} finally {
			pool.release(broker);
		}
	}

	/**
	 * Sets the content for this resource. If value is of type File, it is
	 * directly passed to the parser when Collection.storeResource is called.
	 * Otherwise the method tries to convert the value to String.
	 * 
	 * Passing a File object should be preferred if the document is large. The
	 * file's content will not be loaded into memory but directly passed to a
	 * SAX parser.
	 * 
	 * @param value
	 *                   the content value to set for the resource.
	 * @exception XMLDBException
	 *                         with expected error codes. <br /><code>ErrorCodes.VENDOR_ERROR</code>
	 *                         for any vendor specific errors that occur. <br />
	 */
	public void setContent(Object obj) throws XMLDBException {
		content = null;
		if (obj instanceof File)
			file = (File) obj;
		else if (obj instanceof AtomicValue)
			value = (AtomicValue) obj;
		else {
			content = obj.toString();
		}
	}

	public void setContentAsDOM(Node root) throws XMLDBException {
		this.root = root;
	}

	public ContentHandler setContentAsSAX() throws XMLDBException {
		return new InternalXMLSerializer();
	}

	private class InternalXMLSerializer extends SAXSerializer {

		public InternalXMLSerializer() {
			super(new StringWriter(), null);
		}

		/**
		 * @see org.xml.sax.DocumentHandler#endDocument()
		 */
		public void endDocument() throws SAXException {
			super.endDocument();
			content = getWriter().toString();
		}
	}

	public boolean getSAXFeature(String arg0) throws SAXNotRecognizedException,
			SAXNotSupportedException {
		return false;
	}

	public void setSAXFeature(String arg0, boolean arg1)
			throws SAXNotRecognizedException, SAXNotSupportedException {
	}

	/*
	 * (non-Javadoc)
	 * 
	 * @see org.exist.xmldb.EXistResource#getPermissions()
	 */
	public Permission getPermissions() throws XMLDBException {
	    DBBroker broker = null;
	    try {
	        broker = pool.get(user);
		    DocumentImpl document = getDocument(broker, false);
			return document != null ? document.getPermissions() : null;
	    } catch (EXistException e) {
            throw new XMLDBException(ErrorCodes.INVALID_RESOURCE, e.getMessage(), e);
        } finally {
	        pool.release(broker);
	    }
	}
	
	protected void setProperties(Properties properties) {
		this.outputProperties = properties;
	}
	
	private Properties getProperties() {
		return outputProperties == null ? parent.properties : outputProperties;
	}

	protected DocumentImpl getDocument(DBBroker broker, boolean lock) throws XMLDBException {
	    DocumentImpl document = null;
	    if(lock) {
            try {
                document = parent.getCollection().getDocumentWithLock(broker, docId);
            } catch (LockException e) {
                throw new XMLDBException(ErrorCodes.PERMISSION_DENIED,
                        "Failed to acquire lock on document " + docId);
            }
	    } else {
	        document = parent.getCollection().getDocument(broker, docId);
	    }
	    if (document == null) {
	        throw new XMLDBException(ErrorCodes.INVALID_RESOURCE);
	    }
	    return document;
	}
	
	protected NodeProxy getNode() throws XMLDBException {
	    if(proxy != null)
	        return proxy;
	    DBBroker broker = null;
	    try {
	        broker = pool.get(user);
	        DocumentImpl document = getDocument(broker, false);
			if (id < 0)
				// this XMLResource represents a document
				return new NodeProxy(document, 1);
			return new NodeProxy(document, id);
	    } catch (EXistException e) {
            throw new XMLDBException(ErrorCodes.INVALID_RESOURCE, e.getMessage(), e);
        } finally {
	        pool.release(broker);
	    }
	}
}
