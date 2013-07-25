Name:		vertica-social-media-connector
Version:	%{version}
Release:	%{release}
Summary:	HP Vertica Social Media Connector

Group:		Application/Databases
License:	Commercial
Vendor:     Vertica, an HP Company
URL:		http://github.com/vertica/Social-Media-Connector
BuildRoot:	%(mktemp -ud %{_tmppath}/%{name}-%{version}-%{release}-XXXXXX)
Source0:    vertica-social-media-connector-%{version}.tar.gz

%description
HP Vertica Social Media Connector package

%prep
%setup -q

#%build


%install
install -d -m 755  $RPM_BUILD_ROOT/opt/vertica/packages/social-media-connector
rsync -a --exclude=.svn . $RPM_BUILD_ROOT/opt/vertica/packages/social-media-connector/

%clean
rm -rf $RPM_BUILD_ROOT


%files
%defattr(-,root,root,-)
# package manifest, must exactly match content of RPM_BUILD_ROOT
/opt/vertica/packages/social-media-connector
/opt/vertica/packages/social-media-connector/lib
/opt/vertica/packages/social-media-connector/lib/VTweetParser.so
/opt/vertica/packages/social-media-connector/lib/VerticaFlume.jar
/opt/vertica/packages/social-media-connector/ddl
/opt/vertica/packages/social-media-connector/examples
/opt/vertica/packages/social-media-connector/README.md

%post
echo
echo "The HP Vertica Social Media Connector package has been successfully installed on host `hostname`"
echo


%changelog
* Mon Mar 25 2013 Release <release@vertica.com> 
- Initial package

