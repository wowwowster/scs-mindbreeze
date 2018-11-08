<?xml version="1.0" encoding="UTF-8"?>
<xsl:stylesheet xmlns:xsl="http://www.w3.org/1999/XSL/Transform" version="1.0">
    
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
    
    <xsl:variable name="caption">
        
        <xsl:for-each select="attribute">
            <xsl:variable name="currentTag"><xsl:value-of select="name"/></xsl:variable>
            <xsl:if test="$currentTag='caption'">
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
    
    <xsl:variable name="tags">
        
        <xsl:for-each select="attribute">
            <xsl:variable name="currentTag"><xsl:value-of select="name"/></xsl:variable>
            <xsl:if test="$currentTag='tags'">
                <xsl:value-of select="value"/>
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
            <meta name="url" content="{$url}"/>
            <meta name="caption" content="{$caption}"/>
            <meta name="tags" content="{$tags}"/>
            <title><xsl:value-of select="$title"/></title>
        </head>
        <body>
            <table align="center" cellspacing="0" border="1">
                <tr><td><b><center><xsl:value-of select="$title"/></center></b></td></tr>
                <tr><td><img src="{$url}" alt="{$alt}"/></td></tr>
                <xsl:apply-templates/>
            </table>
        </body>
    </html>
    
</xsl:template>
    <!-- match all additionnal attributes -->
  <xsl:template match="attribute">
      <xsl:variable name="currentTag"><xsl:value-of select="name"/></xsl:variable>
      <xsl:if test="$currentTag!='name' and $currentTag!='image'">
          <tr><td><xsl:value-of disable-output-escaping="yes" select="value"/></td></tr>
      </xsl:if>
  </xsl:template>
  
</xsl:stylesheet>
