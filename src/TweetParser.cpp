/*    Copyright 2013 Vertica, an HP Company */

#include "Vertica.h"
using namespace Vertica;

#include <string>
#include <sstream>
#include <iostream>
#include <json/json.h>

class TweetParser : public UDParser {
private:
    const SizedColumnTypes returnType;
    static const char NEWLINE = '\n';
    bool hasRun;
    std::vector< std::vector<std::string> > colNames;
    RejectedRecord rr;
    bool hasRejectedRecord;

    // cast the result to the correct type for the column
    void writeResult(Json::Value result, StreamWriter *writer, size_t column) {
        VerticaType vtype = returnType.getColumnType(column);
        const BaseDataOID typeOid = vtype.getTypeOid();
        switch (typeOid) {
        case Int8OID:
        {
	    if (result.isUInt())
	      writer->setInt(column, result.asUInt64());
	    else if (result.isInt())
	      writer->setInt(column, result.asInt64());
	    else
	      writer->setNull(column);
	    break;
        }
        case Float8OID:
        {
	    if (result.isDouble())
	      writer->setFloat(column, result.asDouble());
	    else
	      writer->setNull(column);
	    break;
        }
        case NumericOID:
        {
	    if (result.isNumeric()) {
	      Vertica::VNumeric tmp = writer->getNumericRef(column);
	      tmp.copy(result.asDouble());
	    } else
	      writer->setNull(column);
	    break;
        }
	case TimestampTzOID:
	  {
	    if (result.isString()) {
	      TimestampTz tz = Vertica::timestamptzIn(result.asString().c_str(), -1, true);
	      writer->setTimestampTz(column, tz);
	    }
	    else
	      writer->setNull(column);
	    break;
	  }
	case TimestampOID:
	  {
	    if (result.isString()) {
	      Timestamp t = Vertica::timestampIn(result.asString().c_str(), -1, true);
	      writer->setTimestamp(column, t);
	    }
	    else
	      writer->setNull(column);
	    break;
	  }	
        case CharOID:
        case VarcharOID:
        case VarbinaryOID:
        case LongVarbinaryOID:
        case LongVarcharOID:
        {
	    if (result.isString()) {
	      Vertica::VString tmp = writer->getStringRef(column);
	      tmp.copy(result.asString());
	    } else
	      writer->setNull(column);
	    break;
        }
        default:
            writer->setNull(column);
        }
    }

    // populate the table for this input row
    bool parseTweet(std::string input, StreamWriter *writer) {
        Json::Reader reader;
        Json::Value result;

        if (!reader.parse(input, result))
	  return false;
        std::ostringstream defName;

        // find the result for each column
        for (size_t j = 0; j < colNames.size(); j++) {
            std::vector<std::string> colParts = colNames[j];
            Json::Value resultCopy = result;

            bool isMember = true;

            // iterate through the column name components
            // Ex: "coordinates.coordinates.0" would have colParts=["coordinates", "coordinates", 0]
            for (size_t k = 0; k < colParts.size(); k++) {
                // non-scalar -- array lookup
                if (colParts[k].find_first_not_of("0123456789") == std::string::npos)
                {
                    std::stringstream ss (colParts[k]);
                    int index;
                    ss >> index;
                    if (resultCopy.isArray() && resultCopy.isValidIndex(index))
                        resultCopy = resultCopy[index];
                    else {
                        isMember = false;
                        break;
                    }
                } 
                // scalar -- direct lookup
                else {
		  if (resultCopy.isArray() || !resultCopy.isMember(colParts[k])) {
                        isMember = false;
                        break;
                    }
                    resultCopy = resultCopy.get(colParts[k], defName.str());
                }
            }
            // this column name was not in the record. set the column value to null
            if (!isMember) 
                writer->setNull(j);
            // found it, write the result 
            else {
                writeResult(resultCopy, writer, j);
            }
        }
	return true;
    }

  void rejectRecord(std::string record, const std::string &reason) {
    RejectedRecord rej(reason, record.c_str(), record.length(),
                          std::string(1, '\n'));
      rr = rej;
    }

public:
    TweetParser(const SizedColumnTypes &returnType) : returnType(returnType) { 
        for (size_t i = 0; i < returnType.getColumnCount(); i++) {
            std::vector<std::string> colParts;
            std::istringstream colName(returnType.getColumnName(i));

            // tokenize the column name by periods in order to iterate through the Json::Value objects later
            std::string s;
            while (std::getline(colName, s, '.')) {
                colParts.push_back(s);
            }
            colNames.push_back(colParts);
        }
	hasRejectedRecord = false;
    }

    virtual StreamState process(ServerInterface &srvInterface, DataBuffer &input, InputState input_state)
    {
        VIAssert(hasRun || input.size >= 65000 || input_state == END_OF_FILE);  // VER-22560
        hasRun = true;

        char * input_buf = (char*)input.buf;
        size_t last_newline_index = 0;
	size_t i;

        for (i = input.offset; i < input.size; i++) {
            if (input_buf[i] == NEWLINE && i != 0) {
                if (last_newline_index != i - 1) {
                    // Copy to a std::string, to get null termination
                    std::string str(&input_buf[last_newline_index], &input_buf[i]);
		    if (parseTweet(str, writer))
		      writer->next();
		    else {
		      rejectRecord(str, "Parsing error. Possibly due to a malformed tweet.");
		      hasRejectedRecord = false;
		      input.offset = i;
		      return REJECT;
		    }
                }
                last_newline_index = i;
            }
        }
        if (input_state == END_OF_FILE) {
            // Special case:
            // If we see "12\n34\n5" in a block, we want to stop after "12\n34\n"
            // because the next block might contain "6\n78" and we don't want to
            // mis-parse the "56" as a "5" and a "6".  (Blocks can split the data
            // stream between any arbitrary two bytes; we have no guarantee of
            // a useful or convenient split there.)
            // However, if "12\n34\n5" is the last block of the file, then
            // we know that there's no following 6 and most likely the user just
            // didn't give us a trailing newline.
            // In that case, parse the "5" as a number.

            if (input.size > 0 && last_newline_index != i) {
                std::string str(&input_buf[last_newline_index], &input_buf[i]);

		parseTweet(str, writer);
            }

            input.offset = input.size;

            return DONE;
        } else {
            input.offset = last_newline_index;
            return INPUT_NEEDED;
        }
    }

    /** Returns information about the rejected record */
    Vertica::RejectedRecord getRejectedRecord() { return rr; }
};

class TweetParserFactory : public ParserFactory {
public:
    virtual void plan(ServerInterface &srvInterface,
            PerColumnParamReader &perColumnParamReader,
            PlanContext &planCtxt) {
    }

    virtual UDParser* prepare(ServerInterface &srvInterface,
            PerColumnParamReader &perColumnParamReader,
            PlanContext &planCtxt,
            const SizedColumnTypes &returnType) {

        return vt_createFuncObject<TweetParser>(srvInterface.allocator, returnType);
    }

    virtual void getParserReturnType(ServerInterface &srvInterface,
            PerColumnParamReader &perColumnParamReader,
            PlanContext &planCtxt,
            const SizedColumnTypes &argTypes,
            SizedColumnTypes &returnType) {
        for (int i = 0; i < argTypes.getColumnCount(); i++) {
            returnType.addArg(argTypes.getColumnType(i), argTypes.getColumnName(i));
        }
    }
};
RegisterFactory(TweetParserFactory);
