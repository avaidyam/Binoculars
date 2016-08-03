# vsurf
target   = GETPOINTS
cc_objects = cp.o output.o read.o sas.o implicit.o curvature.o surface.o options.o main.o
objects  = $(cc_objects)
CXX      = g++
DEPFLAGS = -MM 
CXXFLAGS = -O3 -Wall -I/usr/include/glib-2.0 -I/usr/lib/glib-2.0/include -I$(HOME)/libraries/include -I$(HOME)/libraries/ann/include -static
#LIBS     = -L/bio/maroon/vvenkatr/downloads/gts-0.7.6/lib -lgts -lglib-2.0 -lm -L/bio/maroon/vvenkatr/downloads/ann_1.1.1/lib32 -lANN
LIBS     = -L$(HOME)/libraries/gts/lib -lgts -lglib-2.0 -lm -L$(HOME)/libraries/ann/lib -lANN
LDFLAGS  =

# everything below here is automatic
$(target) : $(objects)
	$(CXX) $(CXXFLAGS) -o $@ $(objects) $(LDFLAGS) $(LIBS)

depends = $(objects:.o=.d)

-include $(depends)

%.d : %.cc
	$(CXX) $(DEPFLAGS) $(CXXFLAGS) $< \
      | sed -e 's,\($*\)\.o:,\1.o $@ : ,g' > $@


.PHONY : clean
clean :
	-rm -f $(target) $(objects) $(depends)



