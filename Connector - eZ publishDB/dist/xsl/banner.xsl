<?xml version="1.0" encoding="UTF-8"?>
<xsl:stylesheet xmlns:xsl="http://www.w3.org/1999/XSL/Transform" version="1.0">
    <!-- Content Attributes you specify here won't be put as metadata -->
    <xsl:variable name="contentAttributes">left_column,center_column,right_column,bottom_column<xsl:text></xsl:text></xsl:variable>
         <!-- Content Types you specify here won't be put as metadata -->
    <xsl:variable name="contentTypes"><xsl:text>ezhtml,eztext</xsl:text></xsl:variable>
   <xsl:template match="content">
        
        <xsl:variable name="baseurl">
            <xsl:text>http://swpvezpub.parisgsa.lan/ezpublish/</xsl:text>
        </xsl:variable>
        
        <xsl:variable name="title">
            
            <xsl:for-each select="attribute">
                <xsl:variable name="currentTag"><xsl:value-of select="name"/></xsl:variable>
                <xsl:if test="$currentTag='name'">
                    <xsl:value-of select="value"/>
                </xsl:if>
            </xsl:for-each>
        </xsl:variable>
        
        <xsl:variable name="url">
            <xsl:for-each select="attribute">
                <xsl:variable name="currentTag"><xsl:value-of select="name"/></xsl:variable>
                <xsl:if test="$currentTag='image'">
                    <xsl:value-of select="concat($baseurl,substring-before(value,' ('))"/>
                </xsl:if>
            </xsl:for-each>
        </xsl:variable>
        
        <xsl:variable name="alt">
            <xsl:for-each select="attribute">
                <xsl:variable name="currentTag"><xsl:value-of select="name"/></xsl:variable>
                <xsl:if test="$currentTag='image'">
                    <xsl:value-of select="substring-before(substring-after(value,'('),')')"/>
                </xsl:if>
            </xsl:for-each>
        </xsl:variable>
        
        <html>
            <head>
                <xsl:call-template name="meta"/>
                <title><xsl:value-of select="$title"/></title>
            </head>
            <body>
                <table align="center" cellspacing="0" border="1">
                    <tr><td><center><img src="{$url}" alt="{$alt}"/></center></td></tr>
                    <xsl:apply-templates/>
                </table>
            </body>
        </html>
        
    </xsl:template>
    <!-- match all additionnal attributes -->
    <xsl:template match="attribute">
        <xsl:variable name="currentTag"><xsl:value-of select="name"/></xsl:variable>
        <xsl:if test="$currentTag!='image' and $currentTag!='image_map'">
            <tr><td><xsl:value-of disable-output-escaping="yes" select="value"/><xsl:text disable-output-escaping="yes">&amp;nbsp;</xsl:text></td></tr>
        </xsl:if>
    </xsl:template>
    
    <xsl:template name="meta">
        <xsl:for-each select="attribute">
            
            <xsl:variable name="currentTagName"><xsl:value-of select="name"/></xsl:variable>
            <xsl:variable name="currentTagValue"><xsl:value-of select="value"/></xsl:variable>
           <xsl:variable name="currentTagType"><xsl:value-of select="datatype"/></xsl:variable>
            <xsl:if test="not(contains($contentAttributes,$currentTagName)) and not(contains($contentTypes,$currentTagType))">
                <meta name="{$currentTagName}" content="{$currentTagValue}"/>
            </xsl:if>
        </xsl:for-each>
    </xsl:template>
    
</xsl:stylesheet>
