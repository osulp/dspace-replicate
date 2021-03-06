/**
 * The contents of this file are subject to the license and copyright
 * detailed in the LICENSE and NOTICE files at the root of the source
 * tree and available online at
 *
 * http://www.dspace.org/license/
 */
package org.dspace.pack.bagit;

import java.io.File;
import java.io.IOException;
import java.sql.SQLException;

import org.dspace.authorize.AuthorizeException;
import org.dspace.content.Bitstream;
import org.dspace.content.Collection;
import org.dspace.content.Community;

import org.dspace.pack.Packer;
import org.dspace.pack.PackerFactory;

import static org.dspace.pack.PackerFactory.*;

/**
 * CommunityPacker Packs and unpacks Community AIPs in Bagit format.
 *
 * @author richardrodgers
 */
public class CommunityPacker implements Packer
{
    // NB - these values must remain synchronized with DB schema -
    // they represent the peristent object state
    private static final String[] fields = {
        "name",
        "short_description",
        "introductory_text",
        "copyright_text",
        "side_bar_text"
    };

    private Community community = null;
    private String archFmt = null;
    
    public CommunityPacker(Community community, String archFmt)
    {
        this.community = community;
        this.archFmt = archFmt;
    }

    public Community getCommunity()
    {
        return community;
    }

    public void setCommunity(Community community)
    {
        this.community = community;
    }

    @Override
    public File pack(File packDir) throws AuthorizeException, SQLException, IOException
    {
        Bag bag = new Bag(packDir);
        // set base object properties
        Bag.FlatWriter fwriter = bag.flatWriter(OBJFILE);
        fwriter.writeProperty(BAG_TYPE, "AIP");
        fwriter.writeProperty(OBJECT_TYPE, "community");
        fwriter.writeProperty(OBJECT_ID, community.getHandle());
        Community parent = community.getParentCommunity();
        if (parent != null)
        {
            fwriter.writeProperty(OWNER_ID, parent.getHandle());
        }
        fwriter.close();
        // then metadata
        Bag.XmlWriter xwriter = bag.xmlWriter("metadata.xml");
        xwriter.startStanza("metadata");
        for (String field : fields)
        {
            String val = community.getMetadata(field);
            if (val != null)
            {
                xwriter.writeValue(field, val);
            }
        }
        xwriter.endStanza();
        xwriter.close();
        // also add logo if it exists
        Bitstream logo = community.getLogo();
        if (logo != null)
        {
            bag.addData("logo", logo.getSize(), logo.retrieve());
        }
        bag.close();
        File archive = bag.deflate(archFmt);
        // clean up undeflated bag
        bag.empty();
        return archive;
    }

    @Override
    public void unpack(File archive) throws AuthorizeException, IOException, SQLException
    {
        if (archive == null)
        {
            throw new IOException("Missing archive for community: " + community.getHandle());
        }
        Bag bag = new Bag(archive);
        // add the metadata
        Bag.XmlReader reader = bag.xmlReader("metadata.xml");
        if (reader != null && reader.findStanza("metadata")) {
            Bag.Value value = null;
            while((value = reader.nextValue()) != null)
            {
                community.setMetadata(value.name, value.val);
            }
            reader.close();
        }
        // also install logo or set to null
        community.setLogo(bag.dataStream("logo"));
        // now write data back to DB
        community.update();
        // clean up bag
        bag.empty();
    }

    @Override
    public long size(String method) throws SQLException
    {
        long size = 0L;
        // logo size, if present
        Bitstream logo = community.getLogo();
        if (logo != null)
        {
            size += logo.getSize();
        }
        // proceed to children, unless 'norecurse' set
        if (! "norecurse".equals(method))
        {
            for (Community comm : community.getSubcommunities())
            {
                size += PackerFactory.instance(comm).size(method);
            }
            for (Collection coll : community.getCollections())
            {
                size += PackerFactory.instance(coll).size(method);
            }
        }
        return size;
    }

    @Override
    public void setContentFilter(String filter)
    {
        // no-op
    }

    @Override
    public void setReferenceFilter(String filter)
    {
        throw new UnsupportedOperationException("Not supported yet.");
    }
}
